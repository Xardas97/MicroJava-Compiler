package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.*;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class MJCompilerTest {
    private static Logger log;

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());

        log = Logger.getLogger(MJCompilerTest.class);
    }

    public static void main(String[] args) {
        Reader reader = null;
        try {
            File sourceCode = new File("test/program.mj");
            reader = new BufferedReader(new FileReader(sourceCode));

            Yylex lexer = new Yylex(reader);
            MJParser parser = new MJParser(lexer);

            Symbol symbol = parser.parse();

            if (!(symbol.value instanceof Program)) {
                log.error("Parsiranje NIJE uspesno zavrseno!");
                return;
            }

            Program program = (Program) symbol.value;
            //log.info(program.toString(""));

            Struct boolType = new Struct(Struct.Bool);
            Tab.init();
            Tab.currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));

            SemanticAnalyzer semAnalyzer = new SemanticAnalyzer(boolType);
            program.traverseBottomUp(semAnalyzer);

            Tab.dump(new DumpSymbolTableVisitorWithBool());

            if (parser.errorDetected || semAnalyzer.errorDetected) {
                if (symbol.value instanceof Program) {
                    log.info(((Program)symbol.value).toString(""));
                }

                log.error("Parsiranje NIJE uspesno zavrseno!");
                return;
            }

            log.info("Parsiranje gotovo!");
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
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
}
