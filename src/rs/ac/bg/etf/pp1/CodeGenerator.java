package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

public class CodeGenerator extends VisitorAdaptor {
    int mainPc = 0;

    public void visit(MethodTypeName methodTypeName) {
        if ("main".equalsIgnoreCase(methodTypeName.getMethodName())) {
            mainPc = Code.pc;
        }

        methodTypeName.obj.setAdr(Code.pc);

        Code.put(Code.enter);
        Code.put(methodTypeName.obj.getLevel());
        Code.put(methodTypeName.obj.getLocalSymbols().size());
    }

    public void visit(MethodDecl methodDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    public void visit(NumConstFctr numConst) {
        Code.loadConst(numConst.getN1());
    }

    public void visit(CharConstFctr charConst) {
        Code.loadConst(charConst.getC1());
    }

    public void visit(BoolConstFctr boolConst) {
        Code.loadConst(boolConst.getB1());
    }

    public void visit(PrintStmt printStmt) {
        if (printStmt.getPrintArg() instanceof NoPrintArg) {
            Code.loadConst(5);
        }
        else {
            int width = ((PrintWidth)printStmt.getPrintArg()).getWidth();
            Code.loadConst(width);
        }

        if(printStmt.getExpr().struct.compatibleWith(Tab.charType)) {
            Code.put(Code.bprint);
        }
        else {
            Code.put(Code.print);
        }
    }
}
