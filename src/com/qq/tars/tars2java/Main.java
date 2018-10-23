package com.qq.tars.tars2java;

import com.qq.tars.tars2java.parse.TarsLexer;
import com.qq.tars.tars2java.parse.TarsParser;
import com.qq.tars.tars2java.parse.ast.TarsNamespace;
import com.qq.tars.tars2java.parse.ast.TarsRoot;
import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CommonTokenStream;

import java.util.*;

public class Main {

    static class arg_handler {
        String flag;
        String para_document;
        String document;
        interface handler {
            int func(int argc, String[] argv);
        }
        handler Ifunc;
        arg_handler (String f, String d, String pd, handler h) {
            flag = f;
            para_document = pd;
            document = d;
            Ifunc = h;
        }
    }

    private static Tars2JavaConfig tars2JavaConfig = new Tars2JavaConfig();
    private static arg_handler[] defargs;
    private static List<String> tarsfiles = new ArrayList<>();

    static {
        defargs = new arg_handler[]{
                new arg_handler("--dir",
                        "generate source file to DIRECTORY",
                        "DIRECTORY",
                        (int argc, String[] argv) -> {
                            tars2JavaConfig.srcPath = argv[0];
                            return 1;
                        }),
                new arg_handler("--tarsFile",
                        "path to tars file, add more files by appending more this arguments.",
                        "FILE PATH",
                        (int argc, String[] argv) -> {
                            tarsfiles.add(argv[0]);
                            return 1;
                        }),
                new arg_handler("--tarsCharset",
                        "The charset of tars files.",
                        "CHARSET",
                        (int argc, String[] argv) -> {
                            tars2JavaConfig.tarsFileCharset = argv[0];
                            return 1;
                        }),
                new arg_handler("--genCharset",
                        "The charset of generated java files.",
                        "CHARSET",
                        (int argc, String[] argv) -> {
                            tarsfiles.add(argv[0]);
                            return 1;
                        }),
                new arg_handler("--servant",
                        "Also generate the java code for servant.",
                        "",
                        (int argc, String[] argv) -> {
                            tars2JavaConfig.servant = true;
                            return 0;
                        }),
                new arg_handler("--prefix",
                        "The class prefix of the generated java code.",
                        "",
                        (int argc, String[] argv) -> {
                            tars2JavaConfig.packagePrefixName = argv[0];
                            return 1;
                        })
        };
    }

    public static void main(String args[]) {

        int defargc = defargs.length;
        int argc = args.length;
        if(argc == 0) {
            printHelp(defargs);
            System.exit(0);
        }
        List<String> argv = new ArrayList<String>(Arrays.asList(args));
        while (argc != 0) {
            if(args[0].equals("--help")) {
                printHelp(defargs);
                System.exit(0);
            } else {
                boolean arg_handled = false;
                for (int i = 0; i < defargc; ++i) {
                    arg_handler a = defargs[i];
                    if(a.flag.equals(argv.get(0))) {
                        argc--;
                        String cur_arg = argv.remove(0);
                        int args_consumed = a.Ifunc.func(argc, argv.toArray(new String[argv.size()]));
                        if (args_consumed < 0) {
                            System.out.println("Failed parsing parameter "+ cur_arg);
                            System.exit(1);
                        }
                        argc -= args_consumed;
                        for(int j=0; j<args_consumed; j++)
                            argv.remove(0);
                        arg_handled = true;
                        break;
                    }
                }
                if (!arg_handled) {
                    argc--;
                    argv.remove(0);
                }
            }
        }

        tars2JavaConfig.tarsFiles = tarsfiles.toArray(new String[0]);

        // 1. check configurations
        if (!tars2JavaConfig.packagePrefixName.endsWith(".")) {
            tars2JavaConfig.packagePrefixName += ".";
        }

        if (tars2JavaConfig.tarsFiles.length == 0) {
            System.out.println("configuration tarsFiles is missing...");
            return;
        }

        Map<String, List<TarsNamespace>> nsMap = new HashMap<String, List<TarsNamespace>>();

        // 2. parse tars files
        for (String tarsFile : tars2JavaConfig.tarsFiles) {
            try {
                System.out.println("Parse " + tarsFile + " ...");
                TarsLexer tarsLexer = new TarsLexer(new ANTLRFileStream(tarsFile, tars2JavaConfig.tarsFileCharset));
                CommonTokenStream tokens = new CommonTokenStream(tarsLexer);
                TarsParser tarsParser = new TarsParser(tokens);
                TarsRoot root = (TarsRoot) tarsParser.start().getTree();
                root.setTokenStream(tokens);
                for (TarsNamespace ns : root.namespaceList()) {
                    List<TarsNamespace> list = nsMap.get(ns.namespace());
                    if (list == null) {
                        list = new ArrayList<TarsNamespace>();
                        nsMap.put(ns.namespace(), list);
                    }
                    list.add(ns);
                }
            } catch (Throwable th) {
                System.out.println("Parse " + tarsFile + " Error!");
            }
        }

        // 3. generate java files.
        for (Map.Entry<String, List<TarsNamespace>> entry : nsMap.entrySet()) {
            try {
                System.out.println("generate code for namespace : " + entry.getKey() + " ...");
                Tars2Java t2j = new Tars2Java(tars2JavaConfig);
                t2j.genJava(entry.getKey(), entry.getValue(), nsMap);
            } catch (Throwable th) {
                System.out.println("generate code for namespace : " + entry.getKey() + " Error!");
            }
        }
    }

    static void printHelp(arg_handler[] args) {
        System.out.println("tars2java command line parameters:");
        for(arg_handler a:args) {
            System.out.println(a.flag+" "+a.para_document);
            System.out.println("\t"+a.document);
        }
    }
}
