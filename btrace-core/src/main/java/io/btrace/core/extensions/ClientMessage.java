/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.core.extensions;

/**
 * Something an extension can send to the BTrace client through {@link
 * ExtensionContext#send(ClientMessage)}.
 *
 * <p>This interface exists so that {@link ExtensionContext}, which the bootstrap class loader
 * defines, does not have to name a wire-protocol type: the protocol classes are not visible to the
 * bootstrap loader. It declares no methods. The only accepted implementations are the protocol
 * commands (subclasses of {@code io.btrace.core.comm.Command}); the runtime rejects any other
 * implementation with an {@link IllegalArgumentException}.
 */
public interface ClientMessage {}
