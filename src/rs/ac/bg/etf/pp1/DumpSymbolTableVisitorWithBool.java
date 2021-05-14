package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.DumpSymbolTableVisitor;

public class DumpSymbolTableVisitorWithBool extends DumpSymbolTableVisitor {
    @Override
    public void visitStructNode(Struct structToVisit) {
        if (structToVisit.getKind() != Struct.Bool) {
            super.visitStructNode(structToVisit);
            return;
        }

        output.append("bool");
    }
}
