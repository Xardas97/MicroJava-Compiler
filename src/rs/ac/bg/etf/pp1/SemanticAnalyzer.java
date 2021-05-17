package rs.ac.bg.etf.pp1;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

public class SemanticAnalyzer extends VisitorAdaptor {
    boolean errorDetected = false;
    int nVars;

    private Struct boolType;
    private Logger log = Logger.getLogger(getClass());

    private Obj currentMethod = null;
    private boolean returnFound = false;

    private Struct currentType = Tab.noType;

    private Struct currentSwitchType = null;
    private boolean yieldFound = false;

    private enum BlockType { WHILE, SWITCH };
    private Stack<BlockType> surroundingBlockTypeStack = new Stack<>();

    public SemanticAnalyzer(Struct boolType) {
        this.boolType = boolType;
    }

    public void visit(ProgName progName) {
        progName.obj = Tab.insert(Obj.Prog, progName.getProgName(), Tab.noType);
        Tab.openScope();
    }

    public void visit(Program program) {
        nVars = Tab.currentScope.getnVars();
        Tab.chainLocalSymbols(program.getProgName().obj);
        Tab.closeScope();
    }

    public void visit(Type type) {
        Obj typeObj = Tab.find(type.getTypeName());

        if (typeObj == Tab.noObj) {
            reportError("Nepostojeci tip: " + type.getTypeName(), type);
            currentType = type.struct = Tab.noType;
            return;
        }

        if (typeObj.getKind() != Obj.Type) {
            reportError("Identifikator nije tip: " + type.getTypeName(), type);
            currentType = type.struct = Tab.noType;
            return;
        }

        currentType = type.struct = typeObj.getType();
    }

    public void visit(MethodTypeName methodTypeName) {
        Struct returnType = Tab.noType;
        if (methodTypeName.getReturnType() instanceof NonVoidReturnType) {
            returnType = ((NonVoidReturnType)methodTypeName.getReturnType()).getType().struct;
        }

        methodTypeName.obj = currentMethod = Tab.insert(Obj.Meth, methodTypeName.getMethodName(), returnType);
        Tab.openScope();

        reportInfo("Obradjuje se funkcija: " + methodTypeName.getMethodName(), methodTypeName);
    }

    public void visit(FormPar formPar) {
        currentMethod.setLevel(currentMethod.getLevel() + 1);
    }

    public void visit(ReturnStmt returnStmt) {
        if (surroundingBlockTypeStack.size() > 0 && surroundingBlockTypeStack.peek() == BlockType.SWITCH) {
            reportError("Return se ne moze koristiti u switch blokovima", returnStmt);
        }

        if (currentMethod.getType() == Tab.noType) {
            reportError("Funkcija tipa void ne sme imati povratnu vrednost", returnStmt);
            return;
        }

        returnFound = true;
        if (areNotCompatible(returnStmt.getExpr().struct, currentMethod.getType())) {
            reportError("Povratna vrednost je pogrešnog tipa", returnStmt);
        }
    }

    public void visit(EmptyReturnStmt returnStmt) {
        if (surroundingBlockTypeStack.size() > 0 && surroundingBlockTypeStack.peek() == BlockType.SWITCH) {
            reportError("Return se ne moze koristiti u switch blokovima", returnStmt);
        }

        if (currentMethod.getType() != Tab.noType) {
            reportError("Return izraz ove funkcije mora imati povratnu vrednost", returnStmt);
            return;
        }
    }

    public void visit(MethodDecl methodDecl) {
        if (!returnFound && currentMethod.getType() != Tab.noType) {
            reportError("Funkciji " + methodDecl.getMethodTypeName().getMethodName() + " fali return naredba", methodDecl);
        }

        Tab.chainLocalSymbols(currentMethod);
        Tab.closeScope();

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
        Obj obj = findInCurrentScope(ident);

        if (obj != Tab.noObj) {
            reportError("Promemljiva " + ident + " je vec deklarisana", varName);
            return;
        }

        Tab.insert(Obj.Var, ident, type);
    }

    public void visit(ConstAssignmentNum constAssign) {
        insertConst(constAssign, constAssign.getConstName(), Tab.intType, constAssign.getN1());
    }

    public void visit(ConstAssignmentChar constAssign) {
        insertConst(constAssign, constAssign.getConstName(), Tab.charType, constAssign.getC1());
    }

    public void visit(ConstAssignmentBool constAssign) {
        insertConst(constAssign, constAssign.getConstName(), boolType, constAssign.getB1());
    }

    public void insertConst(ConstAssign constAssign, String ident, Struct constType, int value) {
        boolean error = false;

        if (areNotCompatible(constType, currentType)) {
            reportError("Konstanti " + ident + " je dodeljen izraz pogresnog tipa", constAssign);
            error = true;
        }

        Obj obj = findInCurrentScope(ident);
        if (obj != Tab.noObj) {
            reportError("Konstanta " + ident + " je vec deklarisana", constAssign);
            error = true;
        }

        if (error) return;

        obj = Tab.insert(Obj.Con, ident, constType);
        obj.setAdr(value);
    }

    public void visit(SingularDesignator designator) {
        designator.obj = getDesignatorObj(designator.getName(), designator);
    }

    public void visit(ArrayName arrayName) {
        String ident = arrayName.getName();
        Obj obj = getDesignatorObj(ident, arrayName);

        if (obj.getType().getKind() != Struct.Array) {
            reportError("Identifikatora " + ident + " nije niz", arrayName);
            obj = Tab.noObj;
        }

        arrayName.obj = obj;
    }

    public void visit(ArrayDesignator designator) {
        String ident = designator.getArrayName().getName();

        if (designator.getExpr().struct != Tab.intType) {
            reportError("Index niza mora biti celobrojna vrednost", designator);
        }

        Struct type = designator.getArrayName().obj.getType().getElemType();
        designator.obj = new Obj(Obj.Elem, ident + "_elem", type != null? type: Tab.noType);
    }

    private Obj getDesignatorObj(String ident, SyntaxNode node) {
        Obj obj = Tab.find(ident);

        if (obj == Tab.noObj) {
            reportError("Identifikator " + ident + " ne postoji", node);
            return Tab.noObj;
        }

        if (obj.getKind() != Obj.Var && obj.getKind() != Obj.Con && obj.getKind() != Obj.Meth) {
            reportError("Identifikator " + ident + " se ne moze ovako koristiti", node);
            return Tab.noObj;
        }

        return obj;
    }

    public void visit(MethodCall methodCall) {
        Obj obj = methodCall.getDesignator().obj;

        if (obj == Tab.noObj) {
            methodCall.struct = Tab.noType;
            return;
        }

        if (obj.getKind() != Obj.Meth) {
            reportError("Identifikator " + obj.getName() + " nije funkcija", methodCall);
            methodCall.struct = obj.getType();
            return;
        }

        methodCall.struct = obj.getType();

        List<Struct> actPars = getActParTypes(methodCall.getActPars());

        if (obj.getLevel() != actPars.size()) {
            reportError("Pogresan broj parametara funkcije " + obj.getName(), methodCall);
        }

        if (obj.getLevel() < 1) return;

        List<Struct> formPars = getFormParTypes(obj);
        for (int i = 0; i < Math.min(actPars.size(), formPars.size()); ++i) {
            if (actPars.get(i).getKind() != formPars.get(i).getKind()) {
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
                                   ? Tab.currentScope().values()
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
        factor.struct = Tab.intType;
    }

    public void visit(CharConstFctr factor) {
        factor.struct = Tab.charType;
    }

    public void visit(BoolConstFctr factor) {
        factor.struct = boolType;
    }

    public void visit(ExprFctr factor) {
        factor.struct = factor.getExpr().struct;
    }

    public void visit(ArrayInitFctr factor) {
        if (factor.getExpr().struct != Tab.intType) {
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

        if (operand1 != Tab.intType || operand2 != Tab.intType) {
            reportError("Operacija " + mulopToChar(term.getMulop()) + " se moze koristiti samo sa celobrojnim vrednostima", term);
            term.struct = operand1;
            return;
        }

        term.struct = Tab.intType;
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

        if (operand1 != Tab.intType || operand2 != Tab.intType) {
            reportError("Operacija " + addopToChar(terms.getAddop()) + " se moze koristiti samo sa celobrojnim vrednostima", terms);
            terms.struct = operand1;
            return;
        }

        terms.struct = Tab.intType;
    }

    public void visit(TermListElement terms) {
        terms.struct = terms.getTerm().struct;
    }

    public void visit(SingleExpression expr) {
        expr.struct = expr.getTerm().struct;
    }

    public void visit(SingleExpressionWithNegation expr) {
        Struct struct = expr.getTerm().struct;

        if (struct != Tab.intType) {
            reportError("Mogu se negirati samo celobrojne vrednosti", expr);
        }

        expr.struct = struct;
    }

    public void visit(MultiExpression expr) {
        calculateMultiExpressionType(expr.getTerm().struct, expr.getTerms().struct, expr.getAddop(), expr);
    }

    public void visit(MultiExpressionWithNegation expr) {
        calculateMultiExpressionType(expr.getTerm().struct, expr.getTerms().struct, expr.getAddop(), expr);
    }

    private void calculateMultiExpressionType(Struct operand1, Struct operand2, Addop addop, Expr expr) {
        if (operand1 != Tab.intType || operand2 != Tab.intType) {
            reportError("Operacija " + addopToChar(addop) + " se moze koristiti samo sa celobrojnim vrednostima", expr);
        }

        expr.struct = operand1;
    }

    private char addopToChar(Addop addop) {
        if (addop instanceof Plus) return '+';
        return '-';
    }

    public void visit(CondFactList condFact) {
        Struct s1 = condFact.getCondFact().struct;
        Struct s2 = condFact.getExpr().struct;

        if (areNotCompatible(s1, s2)) {
            reportError("Relacione operacije se ne mogu koristiti sa nekompatibilnim tipovima", condFact);
        }

        if (!typeSupportsRelop(s1, condFact.getRelop())) {
            reportError("Operacija " + relopToString(condFact.getRelop()) + " se ne moze koristiti sa ovim tipovima", condFact);
        }

        condFact.struct = boolType;
    }

    public void visit(CondFactElement condFact) {
        condFact.struct = condFact.getExpr().struct;
    }

    public void visit(CondTermList condTerm) {
        if (condTerm.getCondFact().struct != boolType) {
            reportError("Operacije AND i OR i uslovi se koriste samo sa bool vrednostima", condTerm);
        }
    }

    public void visit(CondTermElement condTerm) {
        if (condTerm.getCondFact().struct != boolType) {
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

        return struct.compatibleWith(Tab.intType) || struct.compatibleWith(Tab.charType);
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

    public void visit(AssignmentStmt assignmentStmt) {
        Obj var = assignmentStmt.getDesignator().obj;

        if (var.getKind() != Obj.Var && var.getKind() != Obj.Elem) {
            reportError("Vrednost se moze dodeljivati samo promenljivivama i nizovima", assignmentStmt);
        }

        if (areNotCompatible(var.getType(), assignmentStmt.getExpr().struct)) {
            reportError("Dodela vrednosti nije moguca, tipovi nisu kompatibilni", assignmentStmt);
        }
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

        if (var.getType() != Tab.intType) {
            reportError("Samo celobrojne vrednosti se mogu inkrementirati ili dekrementirati", stmt);
        }
    }

    public void visit(WhileStart whileStart) {
        surroundingBlockTypeStack.push(BlockType.WHILE);
    }

    public void visit(WhileStmt whileStmt) {
        surroundingBlockTypeStack.pop();
    }

    public void visit(SwitchStart switchStart) {
        surroundingBlockTypeStack.push(BlockType.SWITCH);
    }

    public void visit(Case case_) {
        if (!yieldFound) {
            reportError("Case bloku fali yield naredba", case_);
        }

        yieldFound = false;
    }

    public void visit(SwitchExpr expr) {
        surroundingBlockTypeStack.pop();

        if (!yieldFound) {
            reportError("Default bloku fali yield naredba", expr);
        }

        yieldFound = false;

        expr.struct = currentSwitchType;
        currentSwitchType = null;
    }

    public void visit(YieldStmt yieldStmt) {
        if (surroundingBlockTypeStack.isEmpty() || surroundingBlockTypeStack.peek() != BlockType.SWITCH) {
            reportError("Yield se moze koristiti samo u switch blokovima", yieldStmt);
        }

        yieldFound = true;

        Struct type = yieldStmt.getExpr().struct;
        if (currentSwitchType == null) {
            currentSwitchType = type;
            return;
        }

        if (areNotCompatible(type, currentSwitchType)) {
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

    private Obj findInCurrentScope(String ident) {
        SymbolDataStructure locals = Tab.currentScope.getLocals();
        if (locals == null) return Tab.noObj;

        Obj result = locals.searchKey(ident);
        return result != null? result: Tab.noObj;
    }

    private static boolean areNotCompatible(Struct s1, Struct s2) {
        return !areCompatible(s1, s2);
    }

    private static boolean areCompatible(Struct s1, Struct s2) {
        if (s1 == null || s2 == null) return false;
        return s1.compatibleWith(s2);
    }

    private void reportError(String message, SyntaxNode info) {
        errorDetected = true;
        reportInfo(message, info);
    }

    private void reportInfo(String message, SyntaxNode info) {
        StringBuilder msg = new StringBuilder();
        if (info != null) msg.append("Linija ").append(info.getLine()).append(": ");
        msg.append(message);

        log.info(msg.toString());
    }
}
