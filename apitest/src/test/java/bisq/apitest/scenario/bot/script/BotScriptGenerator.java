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

package bisq.apitest.scenario.bot.script;

import bisq.common.file.JsonFileManager;
import bisq.common.util.Utilities;

import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.getProperty;
import static java.lang.System.out;

@Slf4j
public class BotScriptGenerator {

    private final boolean useTestHarness;
    @Nullable
    private final String countryCode;
    @Nullable
    private final String botPaymentMethodId;
    @Nullable
    private final String paymentAccountIdForBot;
    @Nullable
    private final String paymentAccountIdForCliScripts;
    private final int apiPortForCliScripts;
    private final String actions;
    private final int protocolStepTimeLimitInMinutes;
    private final boolean printCliScripts;
    private final boolean stayAlive;

    public BotScriptGenerator(String[] args) {
        OptionParser parser = new OptionParser();
        var helpOpt = parser.accepts("help", "Print this help text.")
                .forHelp();
        OptionSpec<Boolean> useTestHarnessOpt = parser
                .accepts("use-testharness", "Use the test harness, or manually start your own nodes.")
                .withRequiredArg()
                .ofType(Boolean.class)
                .defaultsTo(true);
        OptionSpec<String> actionsOpt = parser
                .accepts("actions", "A comma delimited list with no spaces, e.g., make,take,take,make,...")
                .withRequiredArg();
        OptionSpec<String> botPaymentMethodIdOpt = parser
                .accepts("bot-payment-method",
                        "The bot's (Bob) payment method id.  If using the test harness,"
                                + " the id will be used to automatically create a payment account.")
                .withRequiredArg();
        OptionSpec<String> countryCodeOpt = parser
                .accepts("country-code",
                        "The two letter country-code for an F2F payment account if using the test harness,"
                                + " but the bot-payment-method option takes precedence.")
                .withRequiredArg();
        OptionSpec<Integer> apiPortForCliScriptsOpt = parser
                .accepts("api-port-for-cli-scripts",
                        "The api port used in bot generated bash/cli scripts.")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);
        OptionSpec<String> paymentAccountIdForBotOpt = parser
                .accepts("payment-account-for-bot",
                        "The bot side's payment account id, when the test harness is not used,"
                                + " and Bob & Alice accounts are not automatically created.")
                .withRequiredArg();
        OptionSpec<String> paymentAccountIdForCliScriptsOpt = parser
                .accepts("payment-account-for-cli-scripts",
                        "The other side's payment account id, used in generated bash/cli scripts when"
                                + " the test harness is not used, and Bob & Alice accounts are not automatically created.")
                .withRequiredArg();
        OptionSpec<Integer> protocolStepTimeLimitInMinutesOpt = parser
                .accepts("step-time-limit", "Each protocol step's time limit in minutes")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(60);
        OptionSpec<Boolean> printCliScriptsOpt = parser
                .accepts("print-cli-scripts", "Print the generated CLI scripts from bot")
                .withRequiredArg()
                .ofType(Boolean.class)
                .defaultsTo(false);
        OptionSpec<Boolean> stayAliveOpt = parser
                .accepts("stay-alive", "Leave test harness nodes running after the last action.")
                .withRequiredArg()
                .ofType(Boolean.class)
                .defaultsTo(true);
        OptionSet options = parser.parse(args);

        if (options.has(helpOpt)) {
            printHelp(parser, out);
            exit(0);
        }

        if (!options.has(actionsOpt)) {
            printHelp(parser, err);
            exit(1);
        }

        this.useTestHarness = options.has(useTestHarnessOpt) ? options.valueOf(useTestHarnessOpt) : true;
        this.actions = options.valueOf(actionsOpt);
        this.apiPortForCliScripts = options.has(apiPortForCliScriptsOpt) ? options.valueOf(apiPortForCliScriptsOpt) : 9998;
        this.botPaymentMethodId = options.has(botPaymentMethodIdOpt) ? options.valueOf(botPaymentMethodIdOpt) : null;
        this.countryCode = options.has(countryCodeOpt) ? options.valueOf(countryCodeOpt) : null;
        this.paymentAccountIdForBot = options.has(paymentAccountIdForBotOpt) ? options.valueOf(paymentAccountIdForBotOpt) : null;
        this.paymentAccountIdForCliScripts = options.has(paymentAccountIdForCliScriptsOpt) ? options.valueOf(paymentAccountIdForCliScriptsOpt) : null;
        this.protocolStepTimeLimitInMinutes = options.valueOf(protocolStepTimeLimitInMinutesOpt);
        this.printCliScripts = options.valueOf(printCliScriptsOpt);
        this.stayAlive = options.valueOf(stayAliveOpt);

        var noPaymentAccountCountryOrMethodForTestHarness = useTestHarness &&
                (!options.has(countryCodeOpt) && !options.has(botPaymentMethodIdOpt));
        if (noPaymentAccountCountryOrMethodForTestHarness) {
            log.error("When running the test harness, payment accounts are automatically generated,");
            log.error("and you must provide one of the following options:");
            log.error(" \t\t(1) --bot-payment-method=<payment-method-id> OR");
            log.error(" \t\t(2) --country-code=<country-code>");
            log.error("If the bot-payment-method option is not present, the bot will create"
                    + " a country based F2F account using the country-code.");
            log.error("If both are present, the bot-payment-method will take precedence.  "
                    + "Currently, only the CLEAR_X_CHANGE_ID bot-payment-method is supported.");
            printHelp(parser, err);
            exit(1);
        }

        var noPaymentAccountIdOrApiPortForCliScripts = !useTestHarness &&
                (!options.has(paymentAccountIdForCliScriptsOpt) || !options.has(paymentAccountIdForBotOpt));
        if (noPaymentAccountIdOrApiPortForCliScripts) {
            log.error("If not running the test harness, payment accounts are not automatically generated,");
            log.error("and you must provide three options:");
            log.error(" \t\t(1) --api-port-for-cli-scripts=<port>");
            log.error(" \t\t(2) --payment-account-for-bot=<payment-account-id>");
            log.error(" \t\t(3) --payment-account-for-cli-scripts=<payment-account-id>");
            log.error("These will be used by the bot and in CLI scripts the bot will generate when creating an offer.");
            printHelp(parser, err);
            exit(1);
        }
    }

    private void printHelp(OptionParser parser, PrintStream stream) {
        try {
            String usage = "Examples\n--------\n"
                    + examplesUsingTestHarness()
                    + examplesNotUsingTestHarness();
            stream.println();
            parser.formatHelpWith(new HelpFormatter());
            parser.printHelpOn(stream);
            stream.println();
            stream.println(usage);
            stream.println();
        } catch (IOException ex) {
            log.error("", ex);
        }
    }

    private String examplesUsingTestHarness() {
        @SuppressWarnings("StringBufferReplaceableByString") StringBuilder builder = new StringBuilder();
        builder.append("To generate a bot-script.json file that will start the test harness,");
        builder.append(" create F2F accounts for Bob and Alice,");
        builder.append(" and take an offer created by Alice's CLI:").append("\n");
        builder.append("\tUsage: BotScriptGenerator").append("\n");
        builder.append("\t\t").append("--use-testharness=true").append("\n");
        builder.append("\t\t").append("--country-code=<country-code>").append("\n");
        builder.append("\t\t").append("--actions=take").append("\n");
        builder.append("\n");
        builder.append("To generate a bot-script.json file that will start the test harness,");
        builder.append(" create Zelle accounts for Bob and Alice,");
        builder.append(" and create an offer to be taken by Alice's CLI:").append("\n");
        builder.append("\tUsage: BotScriptGenerator").append("\n");
        builder.append("\t\t").append("--use-testharness=true").append("\n");
        builder.append("\t\t").append("--bot-payment-method=CLEAR_X_CHANGE").append("\n");
        builder.append("\t\t").append("--actions=make").append("\n");
        builder.append("\n");
        return builder.toString();
    }

    private String examplesNotUsingTestHarness() {
        @SuppressWarnings("StringBufferReplaceableByString") StringBuilder builder = new StringBuilder();
        builder.append("To generate a bot-script.json file that will not start the test harness,");
        builder.append(" but will create useful bash scripts for the CLI user,");
        builder.append(" and make two offers, then take two offers:").append("\n");
        builder.append("\tUsage: BotScriptGenerator").append("\n");
        builder.append("\t\t").append("--use-testharness=false").append("\n");
        builder.append("\t\t").append("--api-port-for-cli-scripts=<port>").append("\n");
        builder.append("\t\t").append("--payment-account-for-bot=<payment-account-id>").append("\n");
        builder.append("\t\t").append("--payment-account-for-cli-scripts=<payment-account-id>").append("\n");
        builder.append("\t\t").append("--actions=make,make,take,take").append("\n");
        builder.append("\n");
        return builder.toString();
    }

    private String generateBotScriptTemplate() {
        return Utilities.objectToJson(new BotScript(
                useTestHarness,
                botPaymentMethodId,
                countryCode,
                paymentAccountIdForBot,
                paymentAccountIdForCliScripts,
                actions.split("\\s*,\\s*").clone(),
                apiPortForCliScripts,
                protocolStepTimeLimitInMinutes,
                printCliScripts,
                stayAlive));
    }

    public static void main(String[] args) {
        BotScriptGenerator generator = new BotScriptGenerator(args);
        String json = generator.generateBotScriptTemplate();
        String destDir = getProperty("java.io.tmpdir");
        JsonFileManager jsonFileManager = new JsonFileManager(new File(destDir));
        jsonFileManager.write(json, "bot-script");
        JsonFileManager.shutDownAllInstances();
        log.info("Saved {}/bot-script.json", destDir);
        log.info("bot-script.json contents\n{}", json);
    }

    // Makes a formatter with a given overall row width of 120 and column separator width of 2.
    private static class HelpFormatter extends BuiltinHelpFormatter {
        public HelpFormatter() {
            super(120, 2);
        }
    }
}
