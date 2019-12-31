/**
 * Copyright 2019 The JoyQueue Authors.
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
package org.joyqueue.broker.limit.domain;

import org.joyqueue.network.transport.Transport;
import org.joyqueue.network.transport.command.Command;

/**
 * LimitContext
 *
 * author: gaohaoxiang
 * date: 2019/5/16
 */
public class LimitContext {

    private Transport transport;
    private Command request;
    private Command response;
    private int delay;

    public LimitContext(Transport transport, Command request, Command response, int delay) {
        this.transport = transport;
        this.request = request;
        this.response = response;
        this.delay = delay;
    }

    public Transport getTransport() {
        return transport;
    }

    public Command getRequest() {
        return request;
    }

    public Command getResponse() {
        return response;
    }

    public int getDelay() {
        return delay;
    }
}