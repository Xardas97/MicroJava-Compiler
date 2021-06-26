package rs.ac.bg.etf.pp1;

import java.util.Stack;
import java.util.List;
import java.util.LinkedList;

import rs.ac.bg.etf.pp1.SemanticAnalyzer.PredeclaredFunctionsUsed;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
    int mainPc = 0;

    private static class WhileInfo {
        int start;
        List<Integer> patchUp = new LinkedList<>();
    }
    private Stack<WhileInfo> currentWhileInfoStack = new Stack<>();

    private int lastRelopOp;
    private List<Integer> conditionFalsePatchAddrs = new LinkedList<>();
    private List<Integer> conditionTruePatchAddrs = new LinkedList<>();

    private Stack<List<Integer>> ifStmtNextJumpPatchAddrStack = new Stack<>();

    private static class SwitchInfo {
        int lastCasePatchAddr;
        List<Integer> endOfSwitchPatchAddrs = new LinkedList<>();
    }
    Stack<SwitchInfo> switchInfoStack = new Stack<>();

    public CodeGenerator(PredeclaredFunctionsUsed predeclared) {
        if (predeclared.chr || predeclared.ord) {
            MyTab.find("chr").setAdr(MyCode.pc);
            MyTab.find("ord").setAdr(MyCode.pc);
            MyCode.put(MyCode.enter);
            MyCode.put(1);
            MyCode.put(1);
            MyCode.put(MyCode.load_n);
            MyCode.put(MyCode.exit);
            MyCode.put(MyCode.return_);
        }

        if (predeclared.len) {
            MyTab.find("len").setAdr(MyCode.pc);
            MyCode.put(MyCode.enter);
            MyCode.put(1);
            MyCode.put(1);
            MyCode.put(MyCode.load_n);
            MyCode.put(MyCode.arraylength);
            MyCode.put(MyCode.exit);
            MyCode.put(MyCode.return_);
        }
    }

    public void visit(MethodTypeName methodTypeName) {
        if ("main".equals(methodTypeName.getMethodName())) {
            mainPc = MyCode.pc;
        }

        methodTypeName.obj.setAdr(MyCode.pc);

        MyCode.put(MyCode.enter);
        MyCode.put(methodTypeName.obj.getLevel());
        MyCode.put(methodTypeName.obj.getLocalSymbols().size());
    }

    public void visit(MethodDecl methodDecl) {
        Struct type = methodDecl.getMethodTypeName().obj.getType();
        if (type == MyTab.noType) {
            MyCode.put(MyCode.exit);
            MyCode.put(MyCode.return_);
        }
    }

    public void visit(ReturnStmt returnStmt) {
        MyCode.put(MyCode.exit);
        MyCode.put(MyCode.return_);
    }

    public void visit(EmptyReturnStmt returnStmt) {
        MyCode.put(MyCode.exit);
        MyCode.put(MyCode.return_);
    }

    public void visit(NumConstFctr numConst) {
        MyCode.loadConst(numConst.getN1());
    }

    public void visit(CharConstFctr charConst) {
        MyCode.loadConst(charConst.getC1());
    }

    public void visit(BoolConstFctr boolConst) {
        MyCode.loadConst(boolConst.getB1());
    }

    public void visit(PrintStmt printStmt) {
        if (printStmt.getPrintArg() instanceof NoPrintArg) {
            MyCode.loadConst(5);
        }
        else {
            int width = ((PrintWidth)printStmt.getPrintArg()).getWidth();
            MyCode.loadConst(width);
        }

        if(printStmt.getExpr().struct.compatibleWith(MyTab.charType)) {
            MyCode.put(MyCode.bprint);
        }
        else {
            MyCode.put(MyCode.print);
        }
    }

    public void visit(ReadStmt readStmt) {
        Obj obj = readStmt.getDesignator().obj;

        if(obj.getType().compatibleWith(MyTab.charType)) {
            MyCode.put(MyCode.bread);
        }
        else {
            MyCode.put(MyCode.read);
        }

        MyCode.store(obj);
    }

    public void visit(SingleAssignmentStmt assignment) {
        if (assignment.getParent() instanceof DoubleAssignmentStmt) {
            MyCode.put(MyCode.dup);
        }

        MyCode.store(assignment.getDesignator().obj);
    }

    public void visit(DoubleAssignmentStmt assignment) {
        if (assignment.getParent() instanceof DoubleAssignmentStmt) {
            MyCode.put(MyCode.dup);
        }

        MyCode.store(assignment.getDesignator().obj);
    }

    public void visit(DesignatorFctr designatorFctr) {
        MyCode.load(designatorFctr.getDesignator().obj);
    }

    public void visit(IncrementStmt incrementStmt) {
        Designator designator = incrementStmt.getDesignator();
        doIncOrDec(designator, MyCode.add);
    }

    public void visit(DecrementStmt decrementStmt) {
        Designator designator = decrementStmt.getDesignator();
        doIncOrDec(designator, MyCode.sub);
    }

    private void doIncOrDec(Designator designator, int cmd) {
        if (designator instanceof ArrayDesignator) {
            MyCode.put(MyCode.dup2);
        }

        MyCode.load(designator.obj);
        MyCode.loadConst(1);
        MyCode.put(cmd);
        MyCode.store(designator.obj);
    }

    public void visit(MethodCall methodCall) {
        Obj methodObj = methodCall.getDesignator().obj;
        int offset = methodObj.getAdr() - MyCode.pc;
        MyCode.put(MyCode.call);
        MyCode.put2(offset);

        if (methodCall.getParent() instanceof MethodCallStmt && methodCall.struct != MyTab.noType) {
            MyCode.put(MyCode.pop);
        }
    }

    public void visit(ArrayName arrayName) {
        MyCode.load(arrayName.obj);
    }

    public void visit(ArrayInitFctr arrayInit) {
        MyCode.put(MyCode.newarray);

        Struct elemType = arrayInit.struct.getElemType();
        if (elemType == MyTab.charType || elemType == MyTab.boolType) {
            MyCode.put(0);
        }
        else {
            MyCode.put(1);
        }
    }

    public void visit(AddopMore addopsMore) {
        int op = getAddopOp(addopsMore.getAddop());
        MyCode.put(op);
    }

    public void visit(TermList term) {
        int op = getAddopOp(term.getAddop());
        MyCode.put(op);
    }

    public void visit(FactorListTerm term) {
        int op = getMulopOp(term.getMulop());
        MyCode.put(op);

        if (term.getParent() instanceof AddopExprWithNegation) {
            MyCode.put(MyCode.neg);
        }
    }

    public void visit(FactorTerm term) {
        if (term.getParent() instanceof AddopExprWithNegation) {
            MyCode.put(MyCode.neg);
        }
    }

    private int getAddopOp(Addop addop) {
        if (addop instanceof Plus) return MyCode.add;
        return MyCode.sub;
    }

    private int getMulopOp(Mulop mulop) {
        if (mulop instanceof Multiple) return MyCode.mul;
        if (mulop instanceof Divide) return MyCode.div;
        return MyCode.rem;
    }

    public void visit(WhileStart whileStart) {
        WhileInfo info = new WhileInfo();
        info.start = MyCode.pc;
        currentWhileInfoStack.push(info);
    }

    private void generateWhileStmtJumps() {
        WhileInfo info = currentWhileInfoStack.pop();

        conditionFalsePatchAddrs.add(MyCode.pc + 1);
        MyCode.putFalseJump(lastRelopOp, 0);

        for(int addr : conditionTruePatchAddrs) {
            MyCode.fixup(addr);
        }
        conditionTruePatchAddrs = new LinkedList<>();

        MyCode.putJump(info.start);

        for(int addr : conditionFalsePatchAddrs) {
            MyCode.fixup(addr);
        }
        conditionFalsePatchAddrs = new LinkedList<>();

        for(int addr : info.patchUp) {
            MyCode.fixup(addr);
        }
    }

    public void visit(ContinueStmt whileStmt) {
        MyCode.putJump(currentWhileInfoStack.peek().start);
    }

    public void visit(BreakStmt breakStmt) {
        currentWhileInfoStack.peek().patchUp.add(MyCode.pc + 1);
        MyCode.putJump(0);
    }

    public void visit(CondFactRelop condFact) {
        lastRelopOp = getRelopOp(condFact.getRelop());
    }

    private int getRelopOp(Relop relop) {
        if (relop instanceof Equal) return MyCode.eq;
        if (relop instanceof NotEqual) return MyCode.ne;
        if (relop instanceof GreaterThan) return MyCode.gt;
        if (relop instanceof LesserThan) return MyCode.lt;
        if (relop instanceof GreaterEqual) return MyCode.ge;
        return MyCode.le;
    }

    public void visit(CondFactSingle condFact) {
        MyCode.loadConst(0);
        lastRelopOp = MyCode.ne;
    }

    public void visit(And and) {
        conditionFalsePatchAddrs.add(MyCode.pc + 1);
        MyCode.putFalseJump(lastRelopOp, 0);
    }

    public void visit(Or or) {
        conditionTruePatchAddrs.add(MyCode.pc + 1);
        MyCode.put(MyCode.jcc + lastRelopOp);
        MyCode.put2(0);

        for(int addr : conditionFalsePatchAddrs) {
            MyCode.fixup(addr);
        }
        conditionFalsePatchAddrs = new LinkedList<>();
    }

    public void visit(ConditionList condition) {
        visit((Condition)condition);
    }

    public void visit(ConditionListElement condition) {
        visit((Condition)condition);
    }

    public void visit(Condition condition) {
        SyntaxNode parent = condition.getParent();
        if (parent instanceof WhileStmt) {
            generateWhileStmtJumps();
            return;
        }

        if (parent instanceof MatchedIfElseStmt || parent instanceof UnmatchedIfElseStmt || parent instanceof UnmatchedIfStmt) {
            generateIfStmtJumps();
            return;
        }
    }

    private void generateIfStmtJumps() {
        conditionFalsePatchAddrs.add(MyCode.pc + 1);
        MyCode.putFalseJump(lastRelopOp, 0);

        ifStmtNextJumpPatchAddrStack.push(conditionFalsePatchAddrs);

        for(int addr : conditionTruePatchAddrs) {
           MyCode.fixup(addr);
        }

        conditionFalsePatchAddrs = new LinkedList<>();
        conditionTruePatchAddrs = new LinkedList<>();
    }

    public void visit(Else else_) {
        List<Integer> lastNextJumpPatchAddrs = ifStmtNextJumpPatchAddrStack.pop();

        List<Integer> nextJumpPatchAddrs = new LinkedList<>();
        nextJumpPatchAddrs.add(MyCode.pc + 1);
        ifStmtNextJumpPatchAddrStack.push(nextJumpPatchAddrs);
        MyCode.putJump(0);

        for(int addr : lastNextJumpPatchAddrs) {
            MyCode.fixup(addr);
        }
    }

    public void visit(MatchedIfElseStmt ifStmt) {
        endIfStmt();
    }

    public void visit(UnmatchedIfElseStmt ifStmt) {
        endIfStmt();
    }

    public void visit(UnmatchedIfStmt ifStmt) {
        endIfStmt();
    }

    private void endIfStmt() {
        List<Integer> nextJumpPatchAddrs = ifStmtNextJumpPatchAddrStack.pop();

        for(int addr : nextJumpPatchAddrs) {
            MyCode.fixup(addr);
        }
    }

    public void visit(SwitchStart switchStart) {
        switchInfoStack.push(new SwitchInfo());
    }

    public void visit(CaseStart caseStart) {
        // Save the switch parameter by duplicating
        MyCode.put(MyCode.dup);

        int number = ((Case)caseStart.getParent()).getN2();
        MyCode.loadConst(number);

        SwitchInfo info = switchInfoStack.peek();
        info.lastCasePatchAddr = MyCode.pc + 1;
        MyCode.putFalseJump(MyCode.eq, 0);
    }

    public void visit(Case case_) {
        SwitchInfo info = switchInfoStack.peek();
        MyCode.fixup(info.lastCasePatchAddr);
    }

    public void visit(YieldStmt yieldStmt) {
        SwitchInfo info = switchInfoStack.peek();
        info.endOfSwitchPatchAddrs.add(MyCode.pc + 1);
        MyCode.putJump(0);
    }

    public void visit(SwitchExpr switchExpr) {
        SwitchInfo info = switchInfoStack.pop();
        for (int addr : info.endOfSwitchPatchAddrs) {
            MyCode.fixup(addr);
        }

        // The top of the stack is the result on the switch expr
        // we need to save it behind the leftover duplicate of the parameter
        // and than delete the leftover parameter duplicate and the result duplicate
        MyCode.put(MyCode.dup_x1);
        MyCode.put(MyCode.pop);
        MyCode.put(MyCode.pop);
    }
}
