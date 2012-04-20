/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.exceptions;

import de.codesourcery.jasm16.utils.ITextRange;
import de.codesourcery.jasm16.utils.TextRange;

public class ParseException extends java.text.ParseException
{
    private final ITextRange range;
    private final Throwable cause;
    
    public ParseException(String s, int offset,int length)
    {
        super(s, offset );
        this.range = new TextRange( offset , length );
        this.cause = null;
    }
    
    public ParseException(String s, int offset,int length,Throwable cause)
    {
        super(s, offset );
        this.range = new TextRange( offset , length );
        this.cause = cause;
    }    
    
    public ParseException(String s, ITextRange range)
    {
        super(s, range.getStartingOffset() );
        this.range = new TextRange( range );
        this.cause = null;
    }
    
    public ParseException(String s, ITextRange range,Throwable cause)
    {
        super(s, range.getStartingOffset() );
        this.range = new TextRange( range );
        this.cause = cause;
    }
    
    public ITextRange getRange()
    {
        return range;
    }
    
    @Override
    public synchronized Throwable getCause()
    {
        return cause;
    }
    
}
