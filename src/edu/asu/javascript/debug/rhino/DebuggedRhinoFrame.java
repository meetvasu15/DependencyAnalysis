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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.dynalang.debug.ExecutionSuspendedEvent;
import org.dynalang.debug.serialized.FunctionValue;
import org.dynalang.debug.serialized.NamedRef;
import org.dynalang.debug.serialized.ObjectValue;
import org.dynalang.debug.serialized.PrimitiveValue;
import org.dynalang.debug.serialized.Ref;
import org.dynalang.debug.serialized.Serialized;
import org.dynalang.debug.serialized.Value;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.debug.DebugFrame;
import org.mozilla.javascript.debug.DebuggableObject;
import org.mozilla.javascript.debug.DebuggableScript;

/**
 * Represents a single call stack frame of a debugged Rhino script execution
 * @author Attila Szegedi
 * @version $Id: DebuggedRhinoFrame.java,v 1.2 2009/02/24 11:54:45 szegedia Exp $
 */
class DebuggedRhinoFrame implements DebugFrame, Serializable
{
    private static final long serialVersionUID = 1L;
    
    private final DebuggableScript scriptOrFn;
    private final DebuggedRhinoFrame caller;
    private final int depth;
    private final DebuggedRhinoExecution execution;
    // activation and thisObj are immutable, but we can't set them in 
    // constructor, so can't make them final...
    private Scriptable activation;
    private Scriptable thisObj;
    
    // The only actually mutable member field
    private int currentLineNumber;
    
    DebuggedRhinoFrame(DebuggableScript scriptOrFn, DebuggedRhinoExecution execution) {
        this.scriptOrFn = scriptOrFn;
        this.execution = execution;
        caller = execution.getTopFrame();
        this.depth = caller == null ? 1 : caller.depth + 1;
    }
    
    public void onLineChange(final Context cx, final int lineNumber) {
        currentLineNumber = lineNumber;
        execution.onLineChange(cx, lineNumber);
    }

    public void onEnter(Context cx, Scriptable activation,
            Scriptable thisObj, Object[] args) {
        this.activation = activation;
        this.thisObj = thisObj;
        execution.setTopFrame(this);
    }

    public void onExceptionThrown(Context cx, Throwable ex) {
        execution.onExceptionThrown(cx, ex);
    }

    public void onExit(Context cx, boolean byThrow, Object resultOrException) {
        execution.onReturn(cx, byThrow, resultOrException);
    }
    
    public void onDebuggerStatement(Context cx) {
        execution.onDebuggerStatement(cx);
    }
    
    ExecutionSuspendedEvent createExecutionSuspendedEvent(final Context cx, 
	    final Throwable exception) {
        return new ExecutionSuspendedEvent()
        {
            public Serialized eval(String expression, int frameIndex, boolean global) {
                if("retval".equals(expression)) {
                    return serialize(execution.getLastReturnValue());
                }
                final boolean oldDisableBreak = execution.isDisableBreak();
                execution.setDisableBreak(true);
                try {
                    return serialize(getFrame(frameIndex, global).eval(
                	    expression, cx));
                }
                finally {
                    execution.setDisableBreak(oldDisableBreak);
                }
            }

            public Throwable getException() {
                return exception;
            }
            
            public Serialized[] lookup(int[] ids) {
        	final Serialized[] retval = new Serialized[ids.length];
        	for(int i = 0; i < ids.length; ++i) {
        	    retval[i] = serialize(handleToObject.get(ids[i]));
        	}
        	return retval;
            }
            
            private final List<Object> handleToObject = new ArrayList<Object>();
	    private final Map<Object, Integer> objectToHandle = new IdentityHashMap<Object, Integer>();
            
            private Ref makeRef(Object obj) {
        	return new Ref(getHandle(obj));
            }
            
            private Integer getHandle(Object obj) {
        	Integer handle = objectToHandle.get(obj);
        	if(handle == null) {
        	    handle = handleToObject.size();
        	    handleToObject.add(obj);
        	    objectToHandle.put(obj, handle);
        	}
        	return handle;
            }
            
            private NamedRef[] serializeProperties(Scriptable s) {
        	final Object[] ids;
        	if(s instanceof DebuggableObject) {
        	    ids = ((DebuggableObject)s).getAllIds();
        	}
        	else {
        	    ids = s.getIds();
        	}
        	final NamedRef[] namedRefs = new NamedRef[ids.length];
        	for(int i = 0; i < ids.length; ++i) {
        	    final String name = String.valueOf(ids[i]);
        	    namedRefs[i] = new NamedRef(name, getHandle(
        		    ScriptableObject.getProperty(s, name)));
        	}
        	return namedRefs;
            }
            
            private Serialized serialize(Object obj) {
        	final int handle = getHandle(obj);
        	if(obj == Undefined.instance) {
        	    return new Value(handle, Value.Type.Undefined);
        	}
        	if(obj == null) {
        	    return new Value(handle, Value.Type.Null);
        	}
        	if(obj instanceof String) {
        	    return new PrimitiveValue(handle, Value.Type.String, obj);
        	}
        	else if(obj instanceof Boolean) {
        	    return new PrimitiveValue(handle, Value.Type.Boolean, obj);
        	}
        	else if(obj instanceof Number) {
        	    return new PrimitiveValue(handle, Value.Type.Number, obj);
        	}
        	else if(obj instanceof Scriptable) {
        	    final Scriptable s = (Scriptable)obj;
        	    if(s instanceof Function) {
        		final String name;
        		final String inferredName;
        		final String source = cx.decompileFunction((Function)s, 4);
        		final Ref scriptRef;
        		if(s instanceof DebuggableScript) {
        		    final DebuggableScript ds = (DebuggableScript)s;
        		    name = inferredName = ds.getFunctionName();
        		    DebuggableScript top = ds;
        		    while(!top.isTopLevel()) {
        			top = top.getParent();
        		    }
        		    // TODO: need to use an ID instead?
        		    scriptRef = makeRef(top);
        		}
        		else {
        		    name = inferredName = null;
        		    scriptRef = makeRef(null);
        		}
        		return new FunctionValue(handle, s.getClassName(), 
        			makeRef(null), makeRef(s.getPrototype()),
        			makeRef(ScriptableObject.getProperty(s, 
        				"prototype")),
        			serializeProperties(s), name, inferredName, 
        			source, scriptRef); 
        	    }
        	    else {
        		return new ObjectValue(handle, s.getClassName(), 
        			makeRef(null), makeRef(s.getPrototype()),
        			makeRef(ScriptableObject.getProperty(s, 
        				"prototype")),
        			serializeProperties(s));
        	    }
        	}
            }
        };
    }
    
    private DebuggedRhinoFrame getFrame(int offset, boolean global) {
	if((global && caller == null) || offset == 0) {
	    return this;
	}
	return caller.getFrame(offset - 1, global);
    }
    
    private Object eval(String expression, Context cx) {
	return ScriptRuntime.evalSpecial(cx, activation, thisObj, 
		new Object[] { expression }, "eval", 0);
    }
    
    String getSourceName() {
        return scriptOrFn.getSourceName();
    }
    
    int getCurrentLineNumber() {
        return currentLineNumber;
    }
    
    Scriptable getActivation() {
        return activation;
    }
    
    int getDepth() {
        return depth;
    }
    
    DebuggedRhinoFrame getCaller() {
        return caller;
    }
}