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

package bisq.core.trade;

import bisq.core.locale.Res;

import bisq.common.proto.ProtoUtil;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Value
public class AutoConfirmResult {
    public enum State {
        UNDEFINED,
        FEATURE_DISABLED,
        TX_NOT_FOUND,
        TX_NOT_CONFIRMED,
        PROOF_OK,
        CONNECTION_FAIL,
        API_FAILURE,
        API_INVALID,
        TX_KEY_REUSED,
        TX_HASH_INVALID,
        TX_KEY_INVALID,
        ADDRESS_INVALID,
        NO_MATCH_FOUND,
        AMOUNT_NOT_MATCHING,
        TRADE_LIMIT_EXCEEDED,
        TRADE_DATE_NOT_MATCHING
    }

    // Only state gets persisted
    private final State state;

    private final transient int confirmCount;
    private final transient int confirmsRequired;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AutoConfirmResult(State state) {
        this(state, 0, 0);
    }

    // alternate constructor for showing confirmation progress information
    public AutoConfirmResult(State state, int confirmCount, int confirmsRequired) {
        this.state = state;
        this.confirmCount = confirmCount;
        this.confirmsRequired = confirmsRequired;
    }

    // alternate constructor for error scenarios
    public AutoConfirmResult(State state, @Nullable String errorMsg) {
        this(state, 0, 0);

        if (isErrorState()) {
            log.error(errorMsg != null ? errorMsg : state.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTOBUF
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.AutoConfirmResult toProtoMessage() {
        return protobuf.AutoConfirmResult.newBuilder().setStateName(state.name()).build();
    }

    public static AutoConfirmResult fromProto(protobuf.AutoConfirmResult proto) {
        AutoConfirmResult.State state = ProtoUtil.enumFromProto(AutoConfirmResult.State.class, proto.getStateName());
        return state != null ? new AutoConfirmResult(state) : new AutoConfirmResult(State.UNDEFINED);

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getTextStatus() {
        switch (state) {
            case TX_NOT_CONFIRMED:
                return Res.get("portfolio.pending.autoConfirmPending")
                        + " " + confirmCount
                        + "/" + confirmsRequired;
            case TX_NOT_FOUND:
                return Res.get("portfolio.pending.autoConfirmTxNotFound");
            case FEATURE_DISABLED:
                return Res.get("portfolio.pending.autoConfirmDisabled");
            case PROOF_OK:
                return Res.get("portfolio.pending.autoConfirmSuccess");
            default:
                // any other statuses we display the enum name
                return this.state.toString();
        }
    }

    public boolean isPendingState() {
        return (state == State.TX_NOT_FOUND || state == State.TX_NOT_CONFIRMED);
    }

    public boolean isSuccessState() {
        return (state == State.PROOF_OK);
    }

    public boolean isErrorState() {
        return (!isPendingState() && !isSuccessState());
    }
}