package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.SymbolTableVisitor;

public class MyTab extends Tab {
    public static final Struct boolType = new Struct(Struct.Bool);

    public static void init() {
        Tab.init();
        Tab.currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
    }

    public static void dump(Logger log) {
        log.info("==============SADRZAJ TABELE SIMBOLA===================");

        SymbolTableVisitor stv = new DumpSymbolTableVisitorWithBool();
        for (Scope s = Tab.currentScope; s != null; s = s.getOuter()) {
            s.accept(stv);
        }

        log.info("\n" + stv.getOutput());
    }

    public static void dump(SymbolTableVisitor stv) {
        Tab.dump(new DumpSymbolTableVisitorWithBool());
    }
}