package rs.ac.bg.etf.pp1;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.test.CompilerError;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {
    public List<CompilerError> errors = new LinkedList<>();
    public boolean errorDetected = false;
    public int nVars;

    private Logger log = Logger.getLogger(getClass());

    private boolean mainFound = false;

    private Obj currentMethod = null;
    private boolean returnFound = false;

    private Struct currentType = MyTab.noType;

    private Stack<Boolean> inIfBlock = new Stack<>();

    private Stack<Struct> currentSwitchTypeStack = new Stack<>();
    private Stack<Boolean> yieldFoundStack = new Stack<>();

    private enum BlockType { WHILE, SWITCH };
    private Stack<BlockType> surroundingBlockTypeStack = new Stack<>();

    public class PredeclaredFunctionsUsed {
        public boolean chr = false;
        public boolean ord = false;
        public boolean len = false;
    }
    public PredeclaredFunctionsUsed predeclaredFunctionsUsed = new PredeclaredFunctionsUsed();

    public SemanticAnalyzer() { }

    public void visit(ProgName progName) {
        progName.obj = MyTab.insert(Obj.Prog, progName.getProgName(), MyTab.noType);
        MyTab.openScope();
    }

    public void visit(Program program) {
        nVars = MyTab.currentScope.getnVars();
        MyTab.chainLocalSymbols(program.getProgName().obj);
        MyTab.closeScope();

        if (!mainFound) {
            reportError("Mora postojati ulazna metoda 'main'", program);
        }
    }

    public void visit(Type type) {
        Obj typeObj = MyTab.find(type.getTypeName());

        if (typeObj == MyTab.noObj) {
            reportError("Nepostojeci tip: " + type.getTypeName(), type);
            currentType = type.struct = MyTab.noType;
            return;
        }

        if (typeObj.getKind() != Obj.Type) {
            reportError("Identifikator nije tip: " + type.getTypeName(), type);
            currentType = type.struct = MyTab.noType;
            return;
        }

        currentType = type.struct = typeObj.getType();
    }

    public void visit(MethodTypeName methodTypeName) {
        Struct returnType = MyTab.noType;
        if (methodTypeName.getReturnType() instanceof NonVoidReturnType) {
            returnType = ((NonVoidReturnType)methodTypeName.getReturnType()).getType().struct;
        }

        methodTypeName.obj = currentMethod = MyTab.insert(Obj.Meth, methodTypeName.getMethodName(), returnType);
        MyTab.openScope();

        reportElement("Nadjeno", methodTypeName.obj, methodTypeName);
    }

    public void visit(FormPar formPar) {
        currentMethod.setLevel(currentMethod.getLevel() + 1);
    }

    public void visit(ReturnStmt returnStmt) {
        if (surroundingBlockTypeStack.size() > 0 && surroundingBlockTypeStack.peek() == BlockType.SWITCH) {
            reportError("Return se ne moze koristiti u switch blokovima", returnStmt);
        }

        if (currentMethod.getType() == MyTab.noType) {
            reportError("Funkcija tipa void ne sme imati povratnu vrednost", returnStmt);
            return;
        }

        if (inIfBlock.isEmpty()) {
            returnFound = true;
        }

        if (MyTab.areNotCompatible(returnStmt.getExpr().struct, currentMethod.getType())) {
            reportError("Povratna vrednost je pogre??nog tipa", returnStmt);
        }
    }

    public void visit(EmptyReturnStmt returnStmt) {
        if (surroundingBlockTypeStack.size() > 0 && surroundingBlockTypeStack.peek() == BlockType.SWITCH) {
            reportError("Return se ne moze koristiti u switch blokovima", returnStmt);
        }

        if (currentMethod.getType() != MyTab.noType) {
            reportError("Return izraz ove funkcije mora imati povratnu vrednost", returnStmt);
            return;
        }
    }

    public void visit(MethodDecl methodDecl) {
        if (!returnFound && currentMethod.getType() != MyTab.noType) {
            reportError("Funkciji " + methodDecl.getMethodTypeName().getMethodName() + " fali return naredba koja je van grana", methodDecl);
        }

        if ("main".equals(methodDecl.getMethodTypeName().getMethodName())) {
            mainFound = true;

            if (methodDecl.getMethodTypeName().getReturnType() instanceof NonVoidReturnType) {
                reportError("Ulazna metoda 'main' ne sme imati povratnu vrednost", methodDecl);
            }

            if (!(methodDecl.getFormPars() instanceof NoFormPars)) {
                reportError("Ulazna metoda 'main' ne sme imati argumente", methodDecl);
            }
        }

        MyTab.chainLocalSymbols(currentMethod);
        MyTab.closeScope();

        returnFound = false;
        currentMethod = null;
    }

    public void visit(SingularVarName varName) {
        insertVar(varName, varName.getVarName(), currentType);
    }

    public void visit(ArrayVarName varName) {
        insertVar(varName, varName.getVarName(), new Struct(Struct.Array, currentType));
    }

    private void insertVar(VarName varName, String ident, Struct type) {
        Obj obj = MyTab.findInCurrentScope(ident);

        if (obj != MyTab.noObj) {
            reportError("Promemljiva " + ident + " je vec deklarisana", varName);
            return;
        }

        obj = MyTab.insert(Obj.Var, ident, type);
        reportElement("Nadjeno", obj, varName);
    }

    public void visit(ConstAssignmentNum constAssign) {
        insertConst(constAssign, constAssign.getConstName(), MyTab.intType, constAssign.getN1());
    }

    public void visit(ConstAssignmentChar constAssign) {
        insertConst(constAssign, constAssign.getConstName(), MyTab.charType, constAssign.getC1());
    }

    public void visit(ConstAssignmentBool constAssign) {
        insertConst(constAssign, constAssign.getConstName(), MyTab.boolType, constAssign.getB1());
    }

    public void insertConst(ConstAssign constAssign, String ident, Struct constType, int value) {
        boolean error = false;

        if (MyTab.areNotCompatible(constType, currentType)) {
            reportError("Konstanti " + ident + " je dodeljen izraz pogresnog tipa", constAssign);
            error = true;
        }

        Obj obj = MyTab.findInCurrentScope(ident);
        if (obj != MyTab.noObj) {
            reportError("Konstanta " + ident + " je vec deklarisana", constAssign);
            error = true;
        }

        if (error) return;

        obj = MyTab.insert(Obj.Con, ident, constType);
        obj.setAdr(value);

        reportElement("Nadjeno", obj, constAssign);
    }

    public void visit(SingularDesignator designator) {
        designator.obj = getDesignatorObj(designator.getName(), designator);

        if (currentMethod != null && designator.obj.getAdr() < currentMethod.getLevel()) {
            reportElement("Koriscenje argumenta", designator.obj, designator);
        }
    }

    public void visit(ArrayName arrayName) {
        String ident = arrayName.getName();
        Obj obj = getDesignatorObj(ident, arrayName);

        if (obj.getType().getKind() != Struct.Array) {
            reportError("Identifikator " + ident + " nije niz", arrayName);
            obj = MyTab.noObj;
        }

        arrayName.obj = obj;
        reportElement("Pristup", obj, arrayName);

        if (currentMethod != null && arrayName.obj.getAdr() < currentMethod.getLevel()) {
            reportElement("Koriscenje argumenta", arrayName.obj, arrayName);
        }
    }

    public void visit(ArrayDesignator designator) {
        String ident = designator.getArrayName().getName();

        if (designator.getExpr().struct != MyTab.intType) {
            reportError("Index niza mora biti celobrojna vrednost", designator);
        }

        Struct type = designator.getArrayName().obj.getType().getElemType();
        designator.obj = new Obj(Obj.Elem, ident + "_elem", type != null? type: MyTab.noType);
    }

    private Obj getDesignatorObj(String ident, SyntaxNode node) {
        Obj obj = MyTab.find(ident);

        if (obj == MyTab.noObj) {
            reportError("Identifikator " + ident + " ne postoji", node);
            return MyTab.noObj;
        }

        if (obj.getKind() != Obj.Var && obj.getKind() != Obj.Con && obj.getKind() != Obj.Meth) {
            reportError("Identifikator " + ident + " se ne moze ovako koristiti", node);
            return MyTab.noObj;
        }

        return obj;
    }

    public void visit(MethodCall methodCall) {
        Obj obj = methodCall.getDesignator().obj;

        if (obj == MyTab.noObj) {
            methodCall.struct = MyTab.noType;
            return;
        }

        if (obj.getKind() != Obj.Meth) {
            reportError("Identifikator " + obj.getName() + " nije funkcija", methodCall);
            methodCall.struct = obj.getType();
            return;
        }

        reportElement("Poziv", obj, methodCall);

        checkIfPredeclared(obj.getName());

        methodCall.struct = obj.getType();

        List<Struct> actPars = getActParTypes(methodCall.getActPars());

        if (obj.getLevel() != actPars.size()) {
            reportError("Pogresan broj parametara funkcije " + obj.getName(), methodCall);
        }

        if (obj.getLevel() < 1) return;

        List<Struct> formPars = getFormParTypes(obj);
        for (int i = 0; i < Math.min(actPars.size(), formPars.size()); ++i) {
            if (MyTab.areNotCompatible(actPars.get(i), formPars.get(i))) {
                reportError("Parametar #" + (i + 1) + " poziva funkcije " + obj.getName() + " je pogresnog tipa", methodCall);
            }
        }
    }

    private List<Struct> getActParTypes(ActPars actPars) {
        List<Struct> structs = new LinkedList<Struct>();

        if (actPars instanceof NoActPars) return structs;

        ActParList parList = (ActParList) actPars;
        structs.add(parList.getActPar().getExpr().struct);

        ActParsMore currentActParsMore = parList.getActParsMore();

        while(currentActParsMore instanceof ActParsMoreElement) {
            ActParsMoreElement actParsMoreElement = (ActParsMoreElement) currentActParsMore;
            structs.add(actParsMoreElement.getActPar().getExpr().struct);
            currentActParsMore = actParsMoreElement.getActParsMore();
        }

        return structs;
    }

    private List<Struct> getFormParTypes(Obj obj) {
        List<Struct> structs = new LinkedList<Struct>();

        int formParCnt = obj.getLevel();
        Collection<Obj> formPars = currentMethod.equals(obj)
                                   ? MyTab.currentScope().values()
                                   : obj.getLocalSymbols();

        for(Obj formPar: formPars) {
            structs.add(formPar.getType());
            if (--formParCnt < 1) break;
        }

        return structs;
    }

    public void visit(DesignatorFctr factor) {
        factor.struct = factor.getDesignator().obj.getType();
    }

    public void visit(NumConstFctr factor) {
        factor.struct = MyTab.intType;
    }

    public void visit(CharConstFctr factor) {
        factor.struct = MyTab.charType;
    }

    public void visit(BoolConstFctr factor) {
        factor.struct = MyTab.boolType;
    }

    public void visit(ExprFctr factor) {
        factor.struct = factor.getExpr().struct;
    }

    public void visit(ArrayInitFctr factor) {
        if (factor.getExpr().struct != MyTab.intType) {
            reportError("Velicina niza u strukturi mora biti celobrojna vrednost", factor);
        }

        factor.struct = new Struct(Struct.Array, factor.getType().struct);
    }

    public void visit(MethodCallFctr factor) {
        factor.struct = factor.getMethodCall().struct;
    }

    public void visit(FactorListTerm term) {
        Struct operand1 = term.getTerm().struct;
        Struct operand2 = term.getFactor().struct;

        if (operand1 != MyTab.intType || operand2 != MyTab.intType) {
            reportError("Operacija " + mulopToChar(term.getMulop()) + " se moze koristiti samo sa celobrojnim vrednostima", term);
            term.struct = operand1;
            return;
        }

        term.struct = MyTab.intType;
    }

    public void visit(FactorTerm term) {
        term.struct = term.getFactor().struct;
    }

    private char mulopToChar(Mulop mulop) {
        if (mulop instanceof Multiple) return '*';
        if (mulop instanceof Divide) return '/';
        return '%';
    }

    public void visit(TermList terms) {
        Struct operand1 = terms.getTerms().struct;
        Struct operand2 = terms.getTerm().struct;

        if (operand1 != MyTab.intType || operand2 != MyTab.intType) {
            reportError("Operacija " + addopToChar(terms.getAddop()) + " se moze koristiti samo sa celobrojnim vrednostima", terms);
            terms.struct = operand1;
            return;
        }

        terms.struct = MyTab.intType;
    }

    public void visit(TermListElement terms) {
        terms.struct = terms.getTerm().struct;
    }

    public void visit(AddopMore addopMore) {
        Struct struct = addopMore.getTerm().struct;

        if (struct != MyTab.intType) {
            Addop addop = addopMore.getAddop();
            reportError("Operacija " + addopToChar(addop) + " se moze koristiti samo sa celobrojnim vrednostima", addopMore);
            addopMore.struct = MyTab.intType;
            return;
        }

        addopMore.struct = struct;
    }

    public void visit(MoreAddopElements moreAddops) {
		moreAddops.struct = moreAddops.getAddopMore().struct;
    }

    public void visit(AddopExprWithNegation expr) {
        Struct struct = expr.getTerm().struct;

        if (struct != MyTab.intType) {
            reportError("Mogu se negirati samo celobrojne vrednosti", expr);

            if (expr.getMoreAddops() instanceof MoreAddopElements) {
                Addop addop = ((MoreAddopElements)expr.getMoreAddops()).getAddopMore().getAddop();
                reportError("Operacija " + addopToChar(addop) + " se moze koristiti samo sa celobrojnim vrednostima", expr);
            }
        }

        expr.struct = struct;
    }

    public void visit(AddopExpr expr) {
        Struct struct = expr.getTerm().struct;

        if (expr.getMoreAddops() instanceof MoreAddopElements && struct != MyTab.intType) {
            Addop addop = ((MoreAddopElements)expr.getMoreAddops()).getAddopMore().getAddop();
            reportError("Operacija " + addopToChar(addop) + " se moze koristiti samo sa celobrojnim vrednostima", expr);
        }

        expr.struct = expr.getTerm().struct;
    }

    private char addopToChar(Addop addop) {
        if (addop instanceof Plus) return '+';
        return '-';
    }

    public void visit(CondFactRelop condFact) {
        Struct s1 = condFact.getExpr().struct;
        Struct s2 = condFact.getExpr1().struct;

        if (MyTab.areNotCompatible(s1, s2)) {
            reportError("Relacione operacije se ne mogu koristiti sa nekompatibilnim tipovima", condFact);
        }

        if (!typeSupportsRelop(s1, condFact.getRelop())) {
            reportError("Operacija " + relopToString(condFact.getRelop()) + " se ne moze koristiti sa ovim tipovima", condFact);
        }

        condFact.struct = MyTab.boolType;
    }

    public void visit(CondFactSingle condFact) {
        condFact.struct = condFact.getExpr().struct;
    }

    public void visit(CondTermList condTerm) {
        if (condTerm.getCondFact().struct != MyTab.boolType) {
            reportError("Operacije AND i OR i uslovi se koriste samo sa bool vrednostima", condTerm);
        }
    }

    public void visit(CondTermElement condTerm) {
        if (condTerm.getCondFact().struct != MyTab.boolType) {
            reportError("Operacije AND i OR i uslovi se koriste samo sa bool vrednostima", condTerm);
        }
    }

    private String relopToString(Relop relop) {
        if (relop instanceof NotEqual) return "!=";
        if (relop instanceof GreaterThan) return ">";
        if (relop instanceof LesserThan) return "<";
        if (relop instanceof GreaterEqual) return ">=";
        if (relop instanceof LesserEqual) return "<=";
        return "==";
    }

    private boolean typeSupportsRelop(Struct struct, Relop relop) {
        if (relop instanceof Equal || relop instanceof NotEqual)
            return true;

        return struct.compatibleWith(MyTab.intType) || struct.compatibleWith(MyTab.charType);
    }

    public void visit(PrintStmt printStmt) {
        if (printStmt.getExpr().struct.getKind() == Struct.Array) {
            reportError("Ne moze se raditi ispisivanje niza", printStmt);
        }
    }

    public void visit(ReadStmt readStmt) {
        Obj var = readStmt.getDesignator().obj;

        if (var.getKind() != Obj.Var && var.getKind() != Obj.Elem) {
            reportError("Vrednost se moze ucitavati samo u promenljivu ili element niza", readStmt);
        }

        if (var.getType().getKind() == Struct.Array) {
            reportError("Vrednost se ne moze ucitavati u niz", readStmt);
        }
    }

    public void visit(SingleAssignmentStmt assignmentStmt) {
        Obj var = assignmentStmt.getDesignator().obj;
        Expr assigned = assignmentStmt.getExpr();
        verifyAssignmentStmt(assignmentStmt, var, assigned.struct);
    }

    public void visit(DoubleAssignmentStmt assignmentStmt) {
        Obj var = assignmentStmt.getDesignator().obj;
        AssignmentStatement assigned = assignmentStmt.getAssignmentStatement();
        verifyAssignmentStmt(assignmentStmt, var, assigned.struct);
    }

    private void verifyAssignmentStmt(AssignmentStatement assignmentStmt, Obj var, Struct assignedType) {
        if (var.getKind() != Obj.Var && var.getKind() != Obj.Elem) {
            reportError("Vrednost se moze dodeljivati samo promenljivivama i nizovima", assignmentStmt);
        }

        if (MyTab.areNotCompatible(var.getType(), assignedType)) {
            reportError("Dodela vrednosti nije moguca, tipovi nisu kompatibilni", assignmentStmt);
        }

        assignmentStmt.struct = var.getType();
    }

    public void visit(IncrementStmt incrementStmt) {
        Obj var = incrementStmt.getDesignator().obj;
        testIncrementOrDecrementStmt(var, incrementStmt);
    }

    public void visit(DecrementStmt decrementStmt) {
        Obj var = decrementStmt.getDesignator().obj;
        testIncrementOrDecrementStmt(var, decrementStmt);
    }

    private void testIncrementOrDecrementStmt(Obj var, DesignatorStatement stmt) {
        if (var.getKind() != Obj.Var && var.getKind() != Obj.Elem) {
            reportError("Samo promenljive se mogu inkrementirati ili dekrementirati", stmt);
            return;
        }

        if (var.getType().getKind() == Struct.Array) {
            reportError("Nizovi se ne mogu inkrementirati ili dekrementirati", stmt);
            return;
        }

        if (var.getType() != MyTab.intType) {
            reportError("Samo celobrojne vrednosti se mogu inkrementirati ili dekrementirati", stmt);
        }
    }

    public void visit(WhileStart whileStart) {
        surroundingBlockTypeStack.push(BlockType.WHILE);
    }

    public void visit(WhileStmt whileStmt) {
        if (surroundingBlockTypeStack.isEmpty()) {
            // If this happens the error will be reported elsewhere
            return;
        }

        surroundingBlockTypeStack.pop();
    }

    public void visit(SwitchStart switchStart) {
        surroundingBlockTypeStack.push(BlockType.SWITCH);
        currentSwitchTypeStack.push(null);
    }

    public void visit(DefaultStart defaultStart) {
        yieldFoundStack.push(false);
    }

    public void visit(SwitchExpr expr) {
        if (!surroundingBlockTypeStack.isEmpty()) {
            // If the stack is empty the error will be reported elsewhere
            surroundingBlockTypeStack.pop();
        }

        if (!yieldFoundStack.isEmpty()) {
            // If the stack is empty the error will be reported elsewhere
            if (!yieldFoundStack.pop()) {
                reportError("Default bloku fali yield naredba", expr);
            }
        }

        if (currentSwitchTypeStack.isEmpty()) {
            // If this happens the error will be reported elsewhere
            expr.struct = MyTab.noType;
            return;
        }

        expr.struct = currentSwitchTypeStack.pop();
    }

    public void visit(YieldStmt yieldStmt) {
        if (surroundingBlockTypeStack.isEmpty() || surroundingBlockTypeStack.peek() != BlockType.SWITCH) {
            reportError("Yield se moze koristiti samo u switch blokovima", yieldStmt);
        }

        if (!yieldFoundStack.empty()) {
            yieldFoundStack.pop();
            yieldFoundStack.push(true);
        }

        if (currentSwitchTypeStack.isEmpty()) {
            // If this happens the error will be reported elsewhere
            return;
        }

        Struct type = yieldStmt.getExpr().struct;
        Struct currentSwitchType = currentSwitchTypeStack.peek();

        if (currentSwitchType == null) {
            currentSwitchTypeStack.pop();
            currentSwitchTypeStack.push(type);
            return;
        }

        if (MyTab.areNotCompatible(type, currentSwitchType)) {
            reportError("Svi blokovi switch-a moraju da vracaju isti tip rezultata", yieldStmt);
        }
    }

    public void visit(BreakStmt breakStmt) {
        if (surroundingBlockTypeStack.isEmpty() || surroundingBlockTypeStack.peek() != BlockType.WHILE) {
            reportError("Break se moze koristiti samo u do-while blokovima", breakStmt);
        }
    }

    public void visit(ContinueStmt continueStmt) {
        if (surroundingBlockTypeStack.isEmpty() || surroundingBlockTypeStack.peek() != BlockType.WHILE) {
            reportError("Continue se moze koristiti samo u do-while blokovima", continueStmt);
        }
    }

    public void visit(IfStart ifStart) {
        inIfBlock.push(true);
    }

    public void visit(MatchedIfElseStmt ifStmt) {
        if (inIfBlock.isEmpty()) {
            // If this happens the error will be reported elsewhere
            return;
        }

        inIfBlock.pop();
    }

    public void visit(UnmatchedIfElseStmt ifStmt) {
        if (inIfBlock.isEmpty()) {
            // If this happens the error will be reported elsewhere
            return;
        }

        inIfBlock.pop();
    }

    public void visit(UnmatchedIfStmt ifStmt) {
        if (inIfBlock.isEmpty()) {
            // If this happens the error will be reported elsewhere
            return;
        }

        inIfBlock.pop();
    }

    private void checkIfPredeclared(String name) {
        if ("chr".equals(name)) predeclaredFunctionsUsed.chr = true;
        if ("ord".equals(name)) predeclaredFunctionsUsed.ord = true;
        if ("len".equals(name)) predeclaredFunctionsUsed.len = true;
    }

    private void reportElement(String messageHead, Obj obj, SyntaxNode node) {
        StringBuilder builder = new StringBuilder(messageHead).append(" ");
        builder.append(MyTab.getObjKindString(obj)).append(' ').append(obj.getName()).append(": ");
        builder.append(MyTab.getStructKindString(obj.getType()));
        builder.append(", ").append(obj.getAdr()).append(", ").append(obj.getLevel());

        reportInfo(builder.toString(), node);
    }

    private void reportError(String message, SyntaxNode node) {
        errorDetected = true;
        errors.add(new CompilerError(node.getLine(), message, CompilerError.CompilerErrorType.SEMANTIC_ERROR));
        log.error(formLogMessage(message, node));
    }

    private void reportInfo(String message, SyntaxNode node) {
        log.info(formLogMessage(message, node));
    }

    private String formLogMessage(String message, SyntaxNode node) {
        StringBuilder msg = new StringBuilder();
        if (node != null) msg.append("Linija ").append(node.getLine()).append(": ");
        msg.append(message);

        return msg.toString();
    }
}
