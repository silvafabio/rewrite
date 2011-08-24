/*
 * Copyright 2011 <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ocpsoft.rewrite.param;

import com.ocpsoft.rewrite.config.Operation;
import com.ocpsoft.rewrite.config.OperationBuilder;

/**
 * Defines the interface for an {@link Operation} that wishes to parameterize portions of its behavior.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * 
 */
public interface ParameterizedOperation<P extends ParameterizedOperation<P, T>, T> extends Parameterized<P, T>,
         Operation
{
   /**
    * Chain this {@link Operation} with another {@link Operation} to be performed in series.
    */
   OperationBuilder and(Operation other);
}