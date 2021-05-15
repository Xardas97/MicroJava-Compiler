package rs.ac.bg.etf.pp1;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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

    private boolean returnFound = false;
    private Obj currentMethod = null;
    private Struct currentType = Tab.noType;

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

        currentMethod = Tab.insert(Obj.Meth, methodTypeName.getMethodName(), returnType);
        Tab.openScope();

        reportInfo("Obradjuje se funkcija: " + methodTypeName.getMethodName(), methodTypeName);
    }

    public void visit(FormPar formPar) {
        currentMethod.setLevel(currentMethod.getLevel() + 1);
    }

    public void visit(ReturnStmt returnStmt) {
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
        if (currentMethod.getType() != Tab.noType) {
            reportError("Return izraz ove funkcije mora imati povratnu vrednost", returnStmt);
            return;
        }
    }

    public void visit(MethodDecl methodDecl) {
        if (!returnFound && currentMethod.getType() != Tab.noType) {
            reportError("Funkciji " + methodDecl.getMethodTypeName().getMethodName() + " fali return iskaz", methodDecl);
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

    public void visit(ArrayDesignator designator) {
        String ident = designator.getName();

        if (designator.getExpr().struct != Tab.intType) {
            reportError("Index niza mora biti celobrojna vrednost", designator);
        }

        Obj obj = getDesignatorObj(ident, designator);

        if (obj.getType().getKind() != Struct.Array) {
            reportError("Identifikatora " + ident + " nije niz", designator);
            obj = Tab.noObj;
        }

        designator.obj = new Obj(Obj.Elem, ident + "_elem", obj.getType().getElemType());
    }

    private Obj getDesignatorObj(String ident, Designator designator) {
        Obj obj = Tab.find(ident);

        if (obj == Tab.noObj) {
            reportError("Identifikator " + ident + " ne postoji", designator);
            return Tab.noObj;
        }

        if (obj.getKind() != Obj.Var && obj.getKind() != Obj.Con && obj.getKind() != Obj.Meth) {
            reportError("Identifikator " + ident + " se ne moze ovako koristiti", designator);
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

        List<Struct> actPars = getActualParTypes(methodCall.getActPars());
        if (obj.getLevel() != actPars.size()) {
            reportError("Pogresan broj parametara funkcije " + obj.getName(), methodCall);
        }

        if (obj.getLevel() < 1) return;

        List<Struct> formPars = getFormParTypes(obj);
        for (int i = 0; i < Math.min(actPars.size(), formPars.size()); ++i) {
            if (areNotCompatible(actPars.get(i), formPars.get(i))) {
                reportError("Parametar #" + (i + 1) + " poziva funkcije " + obj.getName() + " je pogresnog tipa", methodCall);
            }
        }
    }

    private List<Struct> getActualParTypes(ActPars actPars) {
        List<Struct> structs = new LinkedList<Struct>();

        if (actPars instanceof NoActPars) return structs;

        ActParList parList = (ActParList) actPars;
        structs.add(parList.getExpr().struct);

        ActParsMore currentActParsMore = parList.getActParsMore();

        while(currentActParsMore instanceof ActParsMoreElement) {
            ActParsMoreElement actParsMoreElement = (ActParsMoreElement) currentActParsMore;
            structs.add(actParsMoreElement.getExpr().struct);
            currentActParsMore = actParsMoreElement.getActParsMore();
        }

        return structs;
    }

    private List<Struct> getFormParTypes(Obj obj) {
        List<Struct> structs = new LinkedList<Struct>();

        int formParCnt = obj.getLevel();
        Collection<Obj> formPars = null;

        if (currentMethod.equals(obj)) {
           formPars = Tab.currentScope().values();
        }
        else {
            formPars = obj.getLocalSymbols();
        }

        for(Obj formPar: formPars) {
            --formParCnt;

            structs.add(formPar.getType());

            if (formParCnt < 1) break;
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
        Struct struct = expr.getTerm().struct;

        if (expr.getNegation() instanceof ExistingNegation && struct != Tab.intType) {
            reportError("Mogu se negirati samo celobrojne vrednosti", expr);
        }

        expr.struct = struct;
    }

    public void visit(MultiExpression expr) {
        Struct operand1 = expr.getTerm().struct;
        Struct operand2 = expr.getTerms().struct;

        if (operand1 != Tab.intType || operand2 != Tab.intType) {
            reportError("Operacija " + addopToChar(expr.getAddop()) + " se moze koristiti samo sa celobrojnim vrednostima", expr);
            expr.struct = operand1;
            return;
        }

        expr.struct = expr.getTerm().struct;
    }

    public void visit(SwitchExpr expr) {
        // TODO implement switch yield
        expr.struct = Tab.intType;
    }

    private char addopToChar(Addop addop) {
        if (addop instanceof Plus) return '+';
        return '-';
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
