/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.protocol.tasks.mediation;

import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.SendPayoutTxPublishedMessage;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendMediatedPayoutTxPublishedMessage extends SendPayoutTxPublishedMessage {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public SendMediatedPayoutTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void setPublishedState() {
    }

    @Override
    protected void setStateArrived() {
    }

    @Override
    protected void setStateStoredInMailbox() {
    }

    @Override
    protected void setStateFault() {
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            super.run();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
