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

package bisq.core.account.witness;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CountryUtil;
import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.payment.ChargeBackRisk;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.arbitration.TraderDataItem;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.Contract;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.security.KeyPair;
import java.security.PublicKey;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static bisq.core.payment.payload.PaymentMethod.getPaymentMethodById;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


// Restricted default Java security policy on Travis does not allow long keys, so test fails.
// Using Utilities.removeCryptographyRestrictions(); did not work.
//@Ignore
public class AccountAgeWitnessServiceTest {
    private PublicKey publicKey;
    private KeyPair keypair;
    private SignedWitnessService signedWitnessService;
    private AccountAgeWitnessService service;
    private ChargeBackRisk chargeBackRisk;
    private FilterManager filterManager;
    private File dir1;
    private File dir2;
    private File dir3;

    @Before
    public void setup() throws IOException {
        KeyRing keyRing = mock(KeyRing.class);
        setupService(keyRing);
        keypair = Sig.generateKeyPair();
        publicKey = keypair.getPublic();
        // Setup temp storage dir
        dir1 = makeDir("temp_tests1");
        dir2 = makeDir("temp_tests1");
        dir3 = makeDir("temp_tests1");
    }

    private void setupService(KeyRing keyRing) {
        chargeBackRisk = mock(ChargeBackRisk.class);
        AppendOnlyDataStoreService dataStoreService = mock(AppendOnlyDataStoreService.class);
        P2PService p2pService = mock(P2PService.class);
        ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        filterManager = mock(FilterManager.class);
        signedWitnessService = new SignedWitnessService(keyRing, p2pService, arbitratorManager, null, appendOnlyDataStoreService, null, filterManager);
        service = new AccountAgeWitnessService(null, null, null, signedWitnessService, chargeBackRisk, null, dataStoreService, null, filterManager);
    }

    private File makeDir(String name) throws IOException {
        var dir = File.createTempFile(name, "");
        dir.delete();
        dir.mkdir();
        return dir;
    }

    @After
    public void tearDown() {
        // Do teardown stuff
    }

    @Ignore
    @Test
    public void testIsTradeDateAfterReleaseDate() {
        Date ageWitnessReleaseDate = new GregorianCalendar(2017, Calendar.OCTOBER, 23).getTime();
        Date tradeDate = new GregorianCalendar(2017, Calendar.NOVEMBER, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 23).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 22, 0, 0, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 22).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 21).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
    }

    @Ignore
    @Test
    public void testVerifySignatureOfNonce() throws CryptoException {
        byte[] nonce = new byte[]{0x01};
        byte[] signature = Sig.sign(keypair.getPrivate(), nonce);
        assertTrue(service.verifySignature(publicKey, nonce, signature, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, nonce, new byte[]{0x02}, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, new byte[]{0x03}, signature, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, new byte[]{0x02}, new byte[]{0x04}, errorMessage -> {
        }));
    }

    @Test
    public void testArbitratorSignWitness() {
        KeyRing buyerKeyRing = new KeyRing(new KeyStorage(dir1));
        KeyRing sellerKeyRing = new KeyRing(new KeyStorage(dir2));

        // Setup dispute for arbitrator to sign both sides
        List<Dispute> disputes = new ArrayList<>();
        PubKeyRing buyerPubKeyRing = buyerKeyRing.getPubKeyRing();
        PubKeyRing sellerPubKeyRing = sellerKeyRing.getPubKeyRing();
        PaymentAccountPayload buyerPaymentAccountPayload = new SepaAccountPayload(PaymentMethod.SEPA_ID, "1", CountryUtil.getAllSepaCountries());
        PaymentAccountPayload sellerPaymentAccountPayload = new SepaAccountPayload(PaymentMethod.SEPA_ID, "2", CountryUtil.getAllSepaCountries());
        AccountAgeWitness buyerAccountAgeWitness = service.getNewWitness(buyerPaymentAccountPayload, buyerPubKeyRing);
        service.addToMap(buyerAccountAgeWitness);
        AccountAgeWitness sellerAccountAgeWitness = service.getNewWitness(sellerPaymentAccountPayload, sellerPubKeyRing);
        service.addToMap(sellerAccountAgeWitness);
        long now = new Date().getTime() + 1000;
        Contract contract = mock(Contract.class);
        disputes.add(new Dispute(new Date().getTime(),
                "trade1",
                0,
                true,
                true,
                buyerPubKeyRing,
                now - 1,
                contract,
                null,
                null,
                null,
                null,
                null,
                "contractAsJson",
                null,
                null,
                null,
                true,
                SupportType.ARBITRATION));
        disputes.get(0).getIsClosedProperty().set(true);
        disputes.get(0).getDisputeResultProperty().set(new DisputeResult(
                "trade1",
                1,
                DisputeResult.Winner.BUYER,
                DisputeResult.Reason.OTHER.ordinal(),
                true,
                true,
                true,
                "summary",
                null,
                null,
                100000,
                0,
                null,
                now - 1,
                false));

        // Filtermanager says nothing is filtered
        when(filterManager.isNodeAddressBanned(any())).thenReturn(false);
        when(filterManager.isCurrencyBanned(any())).thenReturn(false);
        when(filterManager.isPaymentMethodBanned(any())).thenReturn(false);
        when(filterManager.arePeersPaymentAccountDataBanned(any())).thenReturn(false);
        when(filterManager.isWitnessSignerPubKeyBanned(any())).thenReturn(false);

        when(chargeBackRisk.hasChargebackRisk(any(), any())).thenReturn(true);

        when(contract.getPaymentMethodId()).thenReturn(PaymentMethod.SEPA_ID);
        when(contract.getTradeAmount()).thenReturn(Coin.parseCoin("0.01"));
        when(contract.getBuyerPubKeyRing()).thenReturn(buyerPubKeyRing);
        when(contract.getSellerPubKeyRing()).thenReturn(sellerPubKeyRing);
        when(contract.getBuyerPaymentAccountPayload()).thenReturn(buyerPaymentAccountPayload);
        when(contract.getSellerPaymentAccountPayload()).thenReturn(sellerPaymentAccountPayload);
        when(contract.getOfferPayload()).thenReturn(mock(FeeTxOfferPayload.class));
        List<TraderDataItem> items = service.getTraderPaymentAccounts(now, getPaymentMethodById(PaymentMethod.SEPA_ID), disputes);
        assertEquals(2, items.size());

        // Setup a mocked arbitrator key
        ECKey arbitratorKey = mock(ECKey.class);
        when(arbitratorKey.signMessage(any())).thenReturn("1");
        when(arbitratorKey.signMessage(any())).thenReturn("2");
        when(arbitratorKey.getPubKey()).thenReturn("1".getBytes());

        // Arbitrator signs both trader accounts
        items.forEach(item -> service.arbitratorSignAccountAgeWitness(
                item.getTradeAmount(),
                item.getAccountAgeWitness(),
                arbitratorKey,
                item.getPeersPubKey()));

        // Check that both accountAgeWitnesses are signed
        SignedWitness foundBuyerSignedWitness = signedWitnessService.getSignedWitnessSetByOwnerPubKey(
                buyerPubKeyRing.getSignaturePubKeyBytes()).stream()
                .findFirst()
                .orElse(null);
        assert foundBuyerSignedWitness != null;
        assertEquals(Utilities.bytesAsHexString(foundBuyerSignedWitness.getAccountAgeWitnessHash()),
                Utilities.bytesAsHexString(buyerAccountAgeWitness.getHash()));
        SignedWitness foundSellerSignedWitness = signedWitnessService.getSignedWitnessSetByOwnerPubKey(
                sellerPubKeyRing.getSignaturePubKeyBytes()).stream()
                .findFirst()
                .orElse(null);
        assert foundSellerSignedWitness != null;
        assertEquals(Utilities.bytesAsHexString(foundSellerSignedWitness.getAccountAgeWitnessHash()),
                Utilities.bytesAsHexString(sellerAccountAgeWitness.getHash()));
    }

    // Create a tree of signed witnesses Arb -(SWA)-> aew1 -(SW1)-> aew2 -(SW2)-> aew3
    // Delete SWA signature, none of the account age witnesses are considered signed
    // Sign a dummy AccountAgeWitness using the signerPubkey from SW1; aew2 and aew3 are not considered signed. The
    // lost SignedWitness isn't possible to recover so aew1 is still not signed, but it's pubkey is a signer.
    @Test
    public void testArbitratorSignDummyWitness() throws CryptoException {
        ECKey arbitratorKey = new ECKey();
        // Init 2 user accounts
        var user1KeyRing = new KeyRing(new KeyStorage(dir1));
        var user2KeyRing = new KeyRing(new KeyStorage(dir2));
        var user3KeyRing = new KeyRing(new KeyStorage(dir3));
        var pubKeyRing1 = user1KeyRing.getPubKeyRing();
        var pubKeyRing2 = user2KeyRing.getPubKeyRing();
        var pubKeyRing3 = user3KeyRing.getPubKeyRing();
        var account1 = new SepaAccountPayload(PaymentMethod.SEPA_ID, "1", CountryUtil.getAllSepaCountries());
        var account2 = new SepaAccountPayload(PaymentMethod.SEPA_ID, "2", CountryUtil.getAllSepaCountries());
        var account3 = new SepaAccountPayload(PaymentMethod.SEPA_ID, "3", CountryUtil.getAllSepaCountries());
        var aew1 = service.getNewWitness(account1, pubKeyRing1);
        var aew2 = service.getNewWitness(account2, pubKeyRing2);
        var aew3 = service.getNewWitness(account3, pubKeyRing3);
        // Backdate witness1 70 days
        aew1 = new AccountAgeWitness(aew1.getHash(), new Date().getTime() - TimeUnit.DAYS.toMillis(70));
        aew2 = new AccountAgeWitness(aew2.getHash(), new Date().getTime() - TimeUnit.DAYS.toMillis(35));
        aew3 = new AccountAgeWitness(aew3.getHash(), new Date().getTime() - TimeUnit.DAYS.toMillis(1));
        service.addToMap(aew1);
        service.addToMap(aew2);
        service.addToMap(aew3);

        // Test as user1. It's still possible to sign as arbitrator since the ECKey is passed as an argument.
        setupService(user1KeyRing);

        // Arbitrator signs user1
        service.arbitratorSignAccountAgeWitness(aew1, arbitratorKey, pubKeyRing1.getSignaturePubKeyBytes(),
                aew1.getDate());
        // user1 signs user2
        signAccountAgeWitness(aew2, pubKeyRing2.getSignaturePubKey(), aew2.getDate(), user1KeyRing);
        // user2 signs user3
        signAccountAgeWitness(aew3, pubKeyRing3.getSignaturePubKey(), aew3.getDate(), user2KeyRing);
        signedWitnessService.signAndPublishAccountAgeWitness(SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING, aew2,
                pubKeyRing2.getSignaturePubKey());
        assertTrue(service.accountIsSigner(aew1));
        assertTrue(service.accountIsSigner(aew2));
        assertFalse(service.accountIsSigner(aew3));
        assertTrue(signedWitnessService.isSignedAccountAgeWitness(aew3));

        // Remove SignedWitness signed by arbitrator
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        var signedWitnessArb = signedWitnessService.getSignedWitnessMapValues().stream()
                .filter(sw -> sw.getVerificationMethod() == SignedWitness.VerificationMethod.ARBITRATOR)
                .findAny()
                .get();
        signedWitnessService.removeSignedWitness(signedWitnessArb);
        assertEquals(signedWitnessService.getSignedWitnessMapValues().size(), 2);

        // Check that no account age witness is a signer
        assertFalse(service.accountIsSigner(aew1));
        assertFalse(service.accountIsSigner(aew2));
        assertFalse(service.accountIsSigner(aew3));
        assertFalse(signedWitnessService.isSignedAccountAgeWitness(aew2));

        // Sign dummy AccountAgeWitness using signer key from SW_1
        assertEquals(signedWitnessService.getRootSignedWitnessSet(false).size(), 1);

        // TODO: move this to accountagewitnessservice
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        var orphanedSignedWitness = signedWitnessService.getRootSignedWitnessSet(false).stream().findAny().get();
        var dummyAccountAgeWitnessHash = Hash.getRipemd160hash(orphanedSignedWitness.getSignerPubKey());
        var dummyAEW = new AccountAgeWitness(dummyAccountAgeWitnessHash,
                orphanedSignedWitness.getDate() -
                        (TimeUnit.DAYS.toMillis(SignedWitnessService.SIGNER_AGE_DAYS + 1)));
        service.arbitratorSignAccountAgeWitness(
                dummyAEW, arbitratorKey, orphanedSignedWitness.getSignerPubKey(), dummyAEW.getDate());

        assertFalse(service.accountIsSigner(aew1));
        assertTrue(service.accountIsSigner(aew2));
        assertFalse(service.accountIsSigner(aew3));
        assertTrue(signedWitnessService.isSignedAccountAgeWitness(aew2));
    }

    private void signAccountAgeWitness(AccountAgeWitness accountAgeWitness,
                                       PublicKey witnessOwnerPubKey,
                                       long time,
                                       KeyRing signerKeyRing) throws CryptoException {
        byte[] signature = Sig.sign(signerKeyRing.getSignatureKeyPair().getPrivate(), accountAgeWitness.getHash());
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                accountAgeWitness.getHash(),
                signature,
                signerKeyRing.getSignatureKeyPair().getPublic().getEncoded(),
                witnessOwnerPubKey.getEncoded(),
                time,
                SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING.value);
        signedWitnessService.addToMap(signedWitness);
    }

}
