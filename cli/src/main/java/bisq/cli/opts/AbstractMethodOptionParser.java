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

package bisq.cli.opts;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import lombok.Getter;

import static bisq.cli.opts.OptLabel.OPT_HELP;

abstract class AbstractMethodOptionParser implements MethodOpts {

    // The full command line args passed to CliMain.main(String[] args).
    // CLI and Method level arguments are derived from args by an ArgumentList(args).
    protected final String[] args;

    protected final OptionParser parser = new OptionParser();

    // The help option for a specific api method, e.g., takeoffer -help.
    protected final OptionSpec<Void> helpOpt = parser.accepts(OPT_HELP, "Print method help").forHelp();

    @Getter
    protected OptionSet options;
    @Getter
    protected List<String> nonOptionArguments;

    protected AbstractMethodOptionParser(String[] args) {
        this.args = args;
    }

    public AbstractMethodOptionParser parse() {
        try {
            options = parser.parse(new ArgumentList(args).getMethodArguments());
            //noinspection unchecked
            nonOptionArguments = (List<String>) options.nonOptionArguments();
            return this;
        } catch (OptionException ex) {
            throw new IllegalArgumentException(cliExceptionMessageStyle.apply(ex), ex);
        }
    }

    public boolean isForHelp() {
        return options.has(helpOpt);
    }

    protected final Predicate<OptionSpec<String>> valueNotSpecified = (opt) ->
            !options.hasArgument(opt) || options.valueOf(opt).isEmpty();

    private final Function<OptionException, String> cliExceptionMessageStyle = (ex) -> {
        if (ex.getMessage() == null)
            return null;

        var optionToken = "option ";
        var cliMessage = ex.getMessage().toLowerCase();
        if (cliMessage.startsWith(optionToken) && cliMessage.length() > optionToken.length()) {
            cliMessage = cliMessage.substring(cliMessage.indexOf(" ") + 1);
        }
        return cliMessage;
    };
}
