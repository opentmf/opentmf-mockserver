/*
 * This package contains code derived from the MockServer project
 * (https://github.com/mock-server/mockserver) by James D Bloom,
 * originally released under the Apache License, Version 2.0.
 *
 * The original modules used are mockserver-core, mockserver-netty,
 * and mockserver-client-java (v5.15.0).
 *
 * Modifications made by the opentmf-mockserver authors include:
 *   - Migration from Jackson 2.x (com.fasterxml.jackson) to Jackson 3.x (tools.jackson)
 *   - Removal of UI dashboard, proxy/SOCKS, template engines (JavaScript/Velocity),
 *     XML/XPath/XmlSchema body matching, OpenAPI/Swagger, Prometheus metrics,
 *     and javax.servlet dependencies
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
package org.mockserver;
