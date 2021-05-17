package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
    private Struct boolType;

    int mainPc = 0;

    public CodeGenerator(Struct boolType) {
        this.boolType = boolType;
    }

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
        Struct type = methodDecl.getMethodTypeName().obj.getType();
        if (type == Tab.noType) {
            Code.put(Code.exit);
            Code.put(Code.return_);
        }
    }

    public void visit(ReturnStmt returnStmt) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    public void visit(EmptyReturnStmt returnStmt) {
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

    public void visit(ReadStmt readStmt) {
        if(readStmt.getDesignator().obj.getType().compatibleWith(Tab.charType)) {
            Code.put(Code.bread);
        }
        else {
            Code.put(Code.read);
        }

        Code.store(readStmt.getDesignator().obj);
    }

    public void visit(AssignmentStmt assignment) {
        Code.store(assignment.getDesignator().obj);
    }

    public void visit(DesignatorFctr designatorFctr) {
        Code.load(designatorFctr.getDesignator().obj);
    }

    public void visit(IncrementStmt incrementStmt) {
        Designator designator = incrementStmt.getDesignator();
        doIncOrDec(designator, Code.add);
    }

    public void visit(DecrementStmt decrementStmt) {
        Designator designator = decrementStmt.getDesignator();
        doIncOrDec(designator, Code.sub);
    }

    private void doIncOrDec(Designator designator, int cmd) {
        if (designator instanceof ArrayDesignator) {
            Code.put(Code.dup2);
        }

        Code.load(designator.obj);
        Code.loadConst(1);
        Code.put(cmd);
        Code.store(designator.obj);
    }

    public void visit(MethodCall methodCall) {
        Obj methodObj = methodCall.getDesignator().obj;
        int offset = methodObj.getAdr() - Code.pc;
        Code.put(Code.call);
        Code.put2(offset);

        if (methodCall.getParent() instanceof MethodCallStmt && methodCall.struct != Tab.noType) {
            Code.put(Code.pop);
        }
    }

    public void visit(ArrayName arrayName) {
        Code.load(arrayName.obj);
    }

    public void visit(ArrayInitFctr arrayInit) {
        Code.put(Code.newarray);
        if (arrayInit.struct == Tab.charType || arrayInit.struct == boolType) {
            Code.put(0);
        }
        else {
            Code.put(1);
        }
    }

    public void visit(SingleExpressionWithNegation expr) {
        Code.put(Code.neg);
    }

    public void visit(MultiExpression expr) {
        int op = getAddopOp(expr.getAddop());
        Code.put(op);
    }

    public void visit(MultiExpressionWithNegation expr) {
        int op = getAddopOp(expr.getAddop());
        Code.put(op);
    }

    public void visit(TermList term) {
        int op = getAddopOp(term.getAddop());
        Code.put(op);
    }

    public void visit(FactorListTerm term) {
        int op = getMulopOp(term.getMulop());
        Code.put(op);

        if (term.getParent() instanceof MultiExpressionWithNegation) {
            Code.put(Code.neg);
        }
    }

    public void visit(FactorTerm term) {
        if (term.getParent() instanceof MultiExpressionWithNegation) {
            Code.put(Code.neg);
        }
    }

    private int getAddopOp(Addop addop) {
        if (addop instanceof Plus) return Code.add;
        return Code.sub;
    }

    private int getMulopOp(Mulop mulop) {
        if (mulop instanceof Multiple) return Code.mul;
        if (mulop instanceof Divide) return Code.mul;
        return Code.rem;
    }
}
