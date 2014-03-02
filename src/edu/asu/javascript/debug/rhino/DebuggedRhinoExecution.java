/*
   Copyright 2009 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.asu.javascript.debug.rhino;

import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dynalang.debug.Breakpoint;
import org.dynalang.debug.DebuggingSession;
import org.dynalang.debug.DebuggingSessionRegistry;
import org.dynalang.debug.ExecutionSuspendedEvent;
import org.dynalang.debug.ExecutionTerminatedEvent;
import org.dynalang.debug.DebuggingSession.ContinueAction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptRuntime;

/**
 * Encapsulates the debugging state of a script execution. One Rhino program
 * execution should be associated with one execution using the 
 * {@link Context#setDebugger(org.mozilla.javascript.debug.Debugger, Object)}
 * method, passing an instance of this class as the second argument and passing
 * a {@link RhinoDebugger} as the first argument. In case you use Rhino 
 * continuations, you need to keep this object along with the continuation, and
 * restore it into the context (again using {@link Context#setDebugger(
 * org.mozilla.javascript.debug.Debugger, Object)}) when the continuation is
 * ready to be resumed. This object is serializable, and will reconnect to its
 * {@link DebuggingSession} (if it has one) using its ID after deserialization,
 * making it suitable for use even with serialized continuations.
 * @author Attila Szegedi
 * @version $Id: DebuggedRhinoExecution.java,v 1.3 2009/02/24 11:55:31 szegedia Exp $
 */
public class DebuggedRhinoExecution implements Serializable
{
    private static final Logger logger = Logger.getLogger(
	    DebuggedRhinoExecution.class.getName());
    private static final Object COMPILE_ERROR = new Object();
    private static final long serialVersionUID = 1L;
    
    private final String executionId;
    
    private String debuggingSessionId;
    private transient DebuggingSession debuggingSession;
    private transient DebuggingSessionRegistry sessionRegistry;
    
    private DebuggedRhinoFrame topFrame;
    private int stepFrameDepth = -1;
    private DebuggingSession.ContinueAction stepAction;
    private Object lastReturnValue;
    private boolean disableBreak;
    
    /**
     * Creates a new debugged execution state.
     * @param executionId the ID of the execution. It is reported in various
     * events and can help the debugging client identify the execution.
     */
    public DebuggedRhinoExecution(String executionId)
    {
        this.executionId = executionId;
    }
    
    /**
     * Sets the session registry to be used with this debugger. 
     * @param sessionRegistry the session registry to be used with this 
     * debugger. It will be used to find sessions to bind to executions that 
     * hit breakpoints.
     */
    public void setDebuggingSessionRegistry(
	    DebuggingSessionRegistry sessionRegistry) {
	if(sessionRegistry == null) {
	    throw new IllegalArgumentException("debuggingSessionRegistry == null");
	}
	this.sessionRegistry = sessionRegistry;
    }
    
    /**
     * Returns the execution ID (as set by the constructor).
     * @return the execution ID (as set by the constructor).
     */
    public String getExecutionId()
    {
        return executionId;
    }
    
    DebuggedRhinoFrame getTopFrame()
    {
        return topFrame;
    }
    
    void setTopFrame(DebuggedRhinoFrame topFrame)
    {
        this.topFrame = topFrame;
    }
    
    private DebuggingSession getDebuggingSession()
    {
        if(debuggingSession == null)
        {
            // No debugging session
            if(debuggingSessionId != null)
            {
                // Restored from serialization - let's see if our's debugging
                // session is still around
                debuggingSession = sessionRegistry.getDebuggingSession(
                        debuggingSessionId);
                if(debuggingSession == null)
                {
                    debuggingSessionId = null;
                    logger.log(Level.WARNING, "Debugged execution " + executionId + 
                	    " can't find its old debugger session " + 
                	    debuggingSessionId + " on wakeup; detaching " +
                	    "execution from debugger.");
                }
            }
        }
        return debuggingSession;
    }
    
    void onLineChange(Context cx, int lineNumber)
    {
        if(shouldSuspendOnLine(cx, lineNumber)) {
            suspend(cx, topFrame, null);
        }
    }
    
    void onExceptionThrown(Context cx, Throwable t)
    {
        if(shouldSuspendOnException(t)) {
            suspend(cx, topFrame, t);
        }
    }
    
    void onDebuggerStatement(Context cx) {
	suspend(cx, topFrame, null);
    }
    
    /**
     * Set to true to disable breaks in this execution. Internally used
     * to disable breaks during expression evaluation, but can be used 
     * externally too.
     * @param disableBreak true to disable breaks
     */
    public void setDisableBreak(boolean disableBreak) {
        this.disableBreak = disableBreak;
    }

    /**
     * Returns true if breaks are disabled.
     * @return true if breaks are disabled.
     */
    public boolean isDisableBreak() {
	return disableBreak;
    }
    
    void onReturn(Context cx, boolean byThrow, Object resultOrException)
    {
        if(!disableBreak)
        {
            if(!byThrow)
            {
                lastReturnValue = resultOrException;
            }
            switch(stepAction)
            {
                case stepInto:
                {
                    if(!byThrow)
                    {
                        suspend(cx, topFrame.getCaller(), null);
                    }
                    break;
                }
                case stepNext:
                case stepOut:
                {
                    if(byThrow)
                    {
                        // Convert to STEP_INTO so that we suspend as soon as 
                        // we enter a catch or finally block
                        stepAction = ContinueAction.stepInto;
                    }
                    else if(stepFrameDepth >= topFrame.getDepth())
                    {
                        // Note: we're using topFrame.getCaller() so that 
                        // it looks like we have returned from the call.
                        suspend(cx, topFrame.getCaller(), null);
                    }
                    break;
                }
            }
        }
        topFrame = topFrame.getCaller();
    }
    
    private boolean shouldSuspendOnException(Throwable t)
    {
        if(disableBreak) {
            return false;
        }
        final DebuggingSession session = getDebuggingSession();
        if(session != null) {
            if(session.hasBreakpointFor(t)) {
                return true;
            }
        }
        
        // This execution is not associated with a debugging session yet. See if 
        // there's an unbound debugging session with an applicable exception 
        // breakpoint that we can bind to
        for(DebuggingSession dsession: sessionRegistry.getUnboundDebuggingSessions()) {
            if(dsession.hasBreakpointFor(t) && bindToSession(dsession)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean bindToSession(DebuggingSession session)
    {
        // Session bind returns boolean to avoid race conditions among
        // multiple debugged executions wanting to bind to the same 
        // session.
        if(session.bind(executionId))
        {
            debuggingSession = session;
            debuggingSessionId = session.getId();
            return true;
        }
        return false;
    }
    
    private boolean shouldSuspendOnLine(Context cx, int lineNumber)
    {
        if(disableBreak) {
            return false;
        }
        final DebuggingSession session = getDebuggingSession();
        if(session != null) {
            if(stepAction == ContinueAction.stepInto) {
                // Always suspend the debugged execution on new line with STEP_INTO
                return true;
            }
            // Any breakpoints apply to this line?
            if(isBreakpoint(cx, session, topFrame.getSourceName(), lineNumber)) {
                return true;
            }
            // Step over returning to this frame?
            if(stepAction == ContinueAction.stepNext && 
                    stepFrameDepth >= topFrame.getDepth()) {
                return true;
            }
            // None applied
            return false;
        }
        
        // This execution is not associated with a debugging session yet. See if 
        // there's an unbound debugging session with an applicable breakpoint 
        // that we can bind to
        final String sourceName = topFrame.getSourceName();
        for (final DebuggingSession dsession: sessionRegistry.getUnboundDebuggingSessions()) {
            if(isBreakpoint(cx, dsession, sourceName, lineNumber) && bindToSession(dsession)) {
                return true;
            }
        }
        return false;
    }

    Object getLastReturnValue()
    {
        return lastReturnValue;
    }
    
    private void suspend(final Context cx, DebuggedRhinoFrame frame, Throwable t)
    {
        ExecutionSuspendedEvent e = frame.createExecutionSuspendedEvent(cx, t);
        stepAction = debuggingSession.onExecutionSuspended(e);
        switch(stepAction)
        {
            case release:
            {
                debuggingSession = null;
                debuggingSessionId = null;
                break;
            }
            case resume:
            case stepInto:
            {
                break;
            }
            case stepNext:
            case stepOut:
            {
                stepFrameDepth = frame.getDepth();
                break;
            }
            default:
            {
                logger.log(Level.WARNING, "Received unknown action " + 
                	stepAction + " from " + debuggingSession + 
                	", treating it as if it were RESUME");
            }
        }
    }
    
    /**
     * Checks whether any of the breakpoints for the current line number in the
     * specified debugging session is either a non-conditional breakpoint, or 
     * is a conditional breakpoint that evaluates to true.
     * @param session the debugging session providing the breakpoints
     * @param sourceName the source file name of the potential breakpoints
     * @param lineNumber the line number of the potential breakpoints
     * @param cx current Rhino context
     * @return true if at least one of the breakpoints is either a 
     * non-conditional breakpoint, or is a conditional breakpoint that 
     * evaluates to a value that is equivalent to boolean true according to
     * JavaScript type conversion rules.
     */
    private boolean isBreakpoint(Context cx, DebuggingSession session, 
            String sourceName, int lineNumber)
    {
        List<Breakpoint> breakpoints = session.getBreakpointsFor(sourceName, lineNumber);
        if(breakpoints == null)
        {
            return false;
        }
        boolean oldDisableBreak = disableBreak;
        disableBreak = true;
        try
        {
            for (Breakpoint breakpoint : breakpoints) {
                String condition = breakpoint.getCondition();
                if("".equals(condition))
                {
                    // Unconditional breakpoint
                    return true;
                }
                Object compiledCondition = breakpoint.getCompiledCondition();
                if(compiledCondition == COMPILE_ERROR)
                {
                    // Don't bother, we already established it can't be 
                    // compiled.
                    continue;
                }
                if(!(compiledCondition instanceof Script))
                {
                    try
                    {
                        compiledCondition = cx.compileString(condition, "", 1, null);
                        breakpoint.setCompiledCondition(compiledCondition);
                    }
                    catch(Exception e)
                    {
                        breakpoint.setCompiledCondition(COMPILE_ERROR);
                        setConditionEvaluationError(breakpoint, e);
                        continue;
                    }
                }
                try
                {
                    if(ScriptRuntime.toBoolean(((Script)compiledCondition).exec(
                            cx, topFrame.getActivation())))
                    {
                        breakpoint.setConditionEvaluationError(null);
                        return true;
                    }
                    breakpoint.setConditionEvaluationError(null);
                }
                catch(Exception e)
                {
                    if(breakpoint.getLineNumber() != -1)
                    {
                        // Only set evaluation errors on breakpoints associated
                        // to a specific line; reasoning is that breakpoints
                        // that apply anywhere are allowed to be 
                        // non-evaluateable in lots of scopes.
                        setConditionEvaluationError(breakpoint, e);
                    }
                }
            }
        }
        finally
        {
            disableBreak = oldDisableBreak;
        }
        return false;
    }

    private void setConditionEvaluationError(Breakpoint breakpoint, Exception e)
    {
        breakpoint.setConditionEvaluationError(e.getMessage() + 
                " (" + e.getClass().getName() + ")");
    }

    /**
     * Should be invoked by the embedder of the Rhino runtime when the 
     * execution of the program terminates (as Rhino debugger interface doesn't
     * have an event for this).
     * TODO: check whether we can replace this with an exit from the bottommost 
     * stack frame 
     * @param terminationCause an exception that caused execution to terminate,
     * or null if it terminated normally.
     */
    public void onTerminated(final Throwable terminationCause)
    {
        DebuggingSession debuggingSession = getDebuggingSession();
        if(debuggingSession != null)
        {
            debuggingSession.onExecutionTerminated(new ExecutionTerminatedEvent()
            {
                public Throwable getCause()
                {
                    return terminationCause;
                }
            });
        }
    }
}