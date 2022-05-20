package pt.up.fe.comp;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import pt.up.fe.comp.analysis.JmmAnalyser;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.ollir.JmmOptimizer;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

public class Launcher {

    public static void main(String[] args) {
        SpecsSystem.programStandardInit();

        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // read the input code
        if (args.length != 1) {
            throw new RuntimeException("Expected a single argument, a path to an existing input file.");
        }
        File inputFile = new File(args[0]);
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + args[0] + "'.");
        }
        String input = SpecsIo.read(inputFile);

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        config.put("optimize", "false");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");
        System.out.println("first");
        // Instantiate JmmParser
        SimpleParser parser = new SimpleParser();
        System.out.println("asasa");
        // Parse stage
        JmmParserResult parserResult = parser.parse(input, config);
        System.out.println("aaa");
        System.out.println("AST:\n\n" + parserResult.getRootNode().toTree());
        // Check if there are parsing errors
        TestUtils.noErrors(parserResult.getReports());

         // Instantiate JmmAnalysis
        JmmAnalyser analyser = new JmmAnalyser();

        // Analysis stage
        JmmSemanticsResult analysisResult = analyser.semanticAnalysis(parserResult);
        // Check if there are parsing errors
        TestUtils.noErrors(analysisResult.getReports());

        System.out.println("Entrar ollir\n");

         // Instantiate JmmOptimizer
        var optimizer = new JmmOptimizer();

        // Optimization stage
        var optimizationResult = optimizer.optimize(analysisResult);


        // Check if there are parsing errors
        TestUtils.noErrors(optimizationResult);

        // ... add remaining stages
       
    }

}
