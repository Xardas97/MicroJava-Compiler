package rs.ac.bg.etf.pp1;

import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class MyCode extends Code {
    public static void store(Obj o) {
        if (o.getKind() == Obj.Elem && o.getType().getKind() == Struct.Bool) {
            put(bastore);
            return;
        }

        Code.store(o);
    }

    public static void load(Obj o) {
        if (o.getKind() == Obj.Elem && o.getType().getKind() == Struct.Bool) {
            put(baload);
            return;
        }

        Code.load(o);
    }
}