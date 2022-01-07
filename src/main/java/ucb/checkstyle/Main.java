package ucb.checkstyle;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import static ucb.checkstyle.Version.*;

public class Main {

    private static final String CONFIG_FILE = "cs61b_checks.xml";
    private static final String SUPPRESSION_FILE = "cs61b_suppressions.xml";

    public static void main(String... args) {
        String config;
        String suppress;
        ArrayList<String> args1 = new ArrayList<String>();

        config = Main.class.getClassLoader().getResource(CONFIG_FILE)
            .toString();
        suppress = Main.class.getClassLoader().getResource(SUPPRESSION_FILE)
            .toString();

        for (int i = 0; i < args.length; i += 1) {
            String arg = args[i];

            if (arg.equals("--version")) {
                System.err.printf("style61b %s%n", VERSION);
                System.exit(0);
            }

            if (i + 1 == args.length || !arg.startsWith("-")) {
                args1.add(arg);
            } else {
                if (arg.equals("-c")) {
                    config = args[i + 1];
                } else if (arg.equals("-s")) {
                    suppress = args[i + 1];
                } else {
                    args1.add(arg);
                    args1.add(args[i + 1]);
                }
                i += 1;
            }
        }

        System.setProperty("checkstyle.suppress.file", suppress);
        args1.add(0, "-c");
        args1.add(1, config);
        try {
            com.puppycrawl.tools.checkstyle.Main
                .main(args1.toArray(new String[args1.size()]));
        } catch (IOException excp) {
            System.err.printf("Problem reading input: %s", excp);
            System.exit(1);
        }
    }

}
