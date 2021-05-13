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

            if (parser.errorDetected) {
                log.error("Parsiranje NIJE uspesno zavrseno! (Lexer error)");
                return;
            }

            Program program = (Program) symbol.value;
            log.info(program.toString());

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
