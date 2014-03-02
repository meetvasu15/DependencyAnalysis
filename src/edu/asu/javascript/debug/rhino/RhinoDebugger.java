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

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.debug.DebugFrame;
import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.debug.Debugger;

/**
 * Implementation of a Rhino {@link Debugger} interface that provides hooks 
 * into Rhino script executions. Objects of this class are immutable, it is 
 * sufficient to have only one and share it across all Rhino contexts using 
 * {@link Context#setDebugger(Debugger, Object)}.
 * @author Attila Szegedi
 * @version $Id: RhinoDebugger.java,v 1.3 2009/02/23 13:42:51 szegedia Exp $
 */
public class RhinoDebugger implements Debugger
{
    public DebugFrame getFrame(Context cx, DebuggableScript scriptOrFn) {
        return new DebuggedRhinoFrame(scriptOrFn, 
                (DebuggedRhinoExecution)cx.getDebuggerContextData());
    }

    public void handleCompilationDone(Context ctx, DebuggableScript scriptOrFn,
            String source) {
    }
}