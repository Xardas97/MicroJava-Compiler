package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.SemanticAnalyzer.PredeclaredFunctionsUsed;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.test.CompilerError;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;

public class Compiler implements rs.ac.bg.etf.pp1.test.Compiler {
    private static Logger log;
    private List<CompilerError> errors;

    private boolean syntaxErrorDetected;
    private boolean semanticErrorDetected;

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());

        log = Logger.getLogger(Compiler.class);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            log.error("Nedovoljno argumenata");
            return;
        }
        Compiler compiler = new Compiler();
        List<CompilerError> errors = compiler.compile(args[0], args[1]);

        /*System.out.print("==============ERROR LIST===================\n");
        errors.sort(new Comparator<CompilerError>() {
            @Override
            public int compare(CompilerError e1, CompilerError e2) {
                return e1.getLine() - e2.getLine();
            }
        });
        errors.forEach(System.out::println);*/
    }

    @Override
    public List<CompilerError> compile(String sourceFilePath, String outputFilePath) {
        errors = new LinkedList<>();

        try {
            Symbol symbol = parse(sourceFilePath);

            if (!(symbol.value instanceof Program)) {
                log.error("Parsiranje NIJE uspesno zavrseno!");
                return errors;
            }

            Program program = (Program) symbol.value;

            MyTab.init();
            SemanticAnalyzer semAnalyzer = doSemanticAnalysis(program);

            MyTab.dump(log);

            if (syntaxErrorDetected || semanticErrorDetected) {
                log.error("Parsiranje NIJE uspesno zavrseno!");
                return errors;
            }

            log.info("==============SINTAKSNO STABLO===================");
            log.info("\n" + program.toString(""));

            generateCode(program, outputFilePath, semAnalyzer.nVars, semAnalyzer.predeclaredFunctionsUsed);

            return errors;
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            return errors;
        }
    }

    private Symbol parse(String sourceFilePath) throws Exception {
        Reader reader = null;

        try {
            File sourceCode = new File(sourceFilePath);
            reader = new BufferedReader(new FileReader(sourceCode));

            Yylex lexer =  new Yylex(reader);
            MJParser parser = new MJParser(lexer);
            Symbol symbol = parser.parse();

            syntaxErrorDetected = parser.errorDetected;

            errors.addAll(lexer.errors);
            errors.addAll(parser.errors);

            return symbol;
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private SemanticAnalyzer doSemanticAnalysis(Program program) {
        SemanticAnalyzer semAnalyzer = new SemanticAnalyzer();
        program.traverseBottomUp(semAnalyzer);

        errors.addAll(semAnalyzer.errors);
        semanticErrorDetected = semAnalyzer.errorDetected;

        return semAnalyzer;
    }

    private void generateCode(Program program, String outputFilePath, int nVars, PredeclaredFunctionsUsed predeclared) {
        File objFile = new File(outputFilePath);
        if (objFile.exists()) objFile.delete();

        CodeGenerator codeGenerator = new CodeGenerator(predeclared);
        program.traverseBottomUp(codeGenerator);
        Code.dataSize = nVars;
        Code.mainPc = codeGenerator.mainPc;

        try {
            Code.write(new FileOutputStream(objFile));
        } catch (FileNotFoundException e) {
            log.error(e.getMessage(), e);
        }
    }
}
