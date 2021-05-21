package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
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
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.test.CompilerError;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;
import rs.etf.pp1.symboltable.visitors.SymbolTableVisitor;

public class Compiler implements rs.ac.bg.etf.pp1.test.Compiler {
    private static Logger log;
    private List<CompilerError> errors;

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

        System.out.print("==============ERROR LIST===================\n");
        errors.sort(new Comparator<CompilerError>() {
            @Override
            public int compare(CompilerError e1, CompilerError e2) {
                return e1.getLine() - e2.getLine();
            }
        });
        errors.forEach(System.out::println);
    }

    @Override
    public List<CompilerError> compile(String sourceFilePath, String outputFilePath) {
        errors = new LinkedList<>();

        Reader reader = null;

        try {
            File sourceCode = new File(sourceFilePath);
            reader = new BufferedReader(new FileReader(sourceCode));

            Yylex lexer = new Yylex(reader);
            MJParser parser = new MJParser(lexer);

            Symbol symbol = parser.parse();

            errors.addAll(lexer.errors);
            errors.addAll(parser.errors);

            if (!(symbol.value instanceof Program)) {
                log.error("Parsiranje NIJE uspesno zavrseno!");
                return errors;
            }

            Program program = (Program) symbol.value;

            Struct boolType = new Struct(Struct.Bool);
            Tab.init();
            Tab.currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));

            SemanticAnalyzer semAnalyzer = new SemanticAnalyzer(boolType);
            program.traverseBottomUp(semAnalyzer);

            errors.addAll(semAnalyzer.errors);

            log.info("==============SADRZAJ TABELE SIMBOLA===================");
            tsdump();

            if (parser.errorDetected || semAnalyzer.errorDetected) {
                log.error("Parsiranje NIJE uspesno zavrseno!");
                return errors;
            }

            log.info("==============SINTAKSNO STABLO===================");
            log.info("\n" + program.toString(""));

            File objFile = new File(outputFilePath);
            if (objFile.exists()) objFile.delete();

            CodeGenerator codeGenerator = new CodeGenerator(boolType, semAnalyzer.chrCalled, semAnalyzer.ordCalled, semAnalyzer.lenCalled);
            program.traverseBottomUp(codeGenerator);
            Code.dataSize = semAnalyzer.nVars;
            Code.mainPc = codeGenerator.mainPc;
            Code.write(new FileOutputStream(objFile));

            return errors;
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            return errors;
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

    public void tsdump() {
        SymbolTableVisitor stv = new DumpSymbolTableVisitorWithBool();
        for (Scope s = Tab.currentScope; s != null; s = s.getOuter()) {
            s.accept(stv);
        }
        log.info("\n" + stv.getOutput());
    }
}
