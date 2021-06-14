package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;
import rs.etf.pp1.symboltable.visitors.SymbolTableVisitor;

public class MyTab extends Tab {
    public static final Struct boolType = new Struct(Struct.Bool);

    public static void init() {
        Tab.init();
        currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
    }

    public static Obj findInCurrentScope(String ident) {
        SymbolDataStructure locals = currentScope.getLocals();
        if (locals == null) return noObj;

        Obj result = locals.searchKey(ident);
        return result != null? result: noObj;
    }

    public static boolean areNotCompatible(Struct s1, Struct s2) {
        return !areCompatible(s1, s2);
    }

    public static boolean areCompatible(Struct s1, Struct s2) {
        if (s1 == null || s2 == null) return false;

        if (s1.compatibleWith(s2)) return true;

        // Arrays are compatible if one of the elements has no type
        return s1.getKind() == s2.getKind()
               && s1.getKind() == Struct.Array
               && (s1.getElemType() == noType || s2.getElemType() == noType);
    }

    public static String getObjKindString(Obj obj) {
        switch (obj.getKind()) {
            case 0: return "Con";
            case 1: return "Var";
            case 2: return "Type";
            case 3: return "Meth";
            case 4: return "Fld";
            case 5: return "Elem";
            default: return "Prog";
        }
    }

    public static String getStructKindString(Struct struct) {
        String ret = "";
        int kind = struct.getKind();

        if (kind == 3) {
            ret = "Array of ";
            kind = struct.getElemType().getKind();
        }

        switch (kind) {
            case 0: ret += "None"; break;
            case 1: ret += "Int"; break;
            case 2: ret += "Char"; break;
            case 3: ret += "Array"; break;
            case 4: ret += "Class"; break;
            case 5: ret += "Bool"; break;
            case 6: ret += "Enum"; break;
            case 7: ret += "Interface"; break;
        }

        return ret;
    }

    public static void dump(Logger log) {
        log.info("==============SADRZAJ TABELE SIMBOLA===================");

        SymbolTableVisitor stv = new MyDumpSymbolTableVisitor();
        for (Scope s = currentScope; s != null; s = s.getOuter()) {
            s.accept(stv);
        }

        log.info("\n" + stv.getOutput());
    }

    public static void dump(SymbolTableVisitor stv) {
        Tab.dump(new MyDumpSymbolTableVisitor());
    }
}