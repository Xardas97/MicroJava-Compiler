package rs.ac.bg.etf.pp1;

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

    public void visit(ReturnStmt returnStmt) {
        returnFound = true;

        if (returnStmt.getExpr().struct != currentMethod.getType()) {
            reportError("Povratna vrednost je pogrešnog tipa", returnStmt);
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

    public void visit(NumConst constValue) {
        constValue.struct = Tab.intType;
    }

    public void visit(CharConst constValue) {
        constValue.struct = Tab.charType;
    }

    public void visit(BoolConst constValue) {
        constValue.struct = boolType;
    }

    public void visit(ConstAssignment constAssign) {
        boolean error = false;
        String ident = constAssign.getConstName();

        Struct constType = constAssign.getConstValue().struct;
        if (constType != currentType) {
            reportError("Konstanti " + ident + " je dodeljen izraz pogresnog tipa", constAssign);
            error = true;
        }

        Obj obj = findInCurrentScope(ident);
        if (obj != Tab.noObj) {
            reportError("Konstanta " + ident + " je vec deklarisana", constAssign);
            error = true;
        }

        if (error) return;
        Tab.insert(Obj.Con, ident, constType);
    }

    public void visit(SingularDesignator designator) {
        designator.struct = getDesignatorType(designator.getName(), designator, false);
    }

    public void visit(ArrayDesignator designator) {
        String ident = designator.getName();

        if (designator.getExpr().struct != Tab.intType) {
            reportError("Index identifikatora " + ident + " mora biti celobrojna vrednost", designator);
        }

        designator.struct = getDesignatorType(ident, designator, true).getElemType();
    }

    private Struct getDesignatorType(String ident, Designator designator, boolean isArray) {
        Obj obj = Tab.find(ident);

        if (obj == Tab.noObj) {
            reportError("Identifikator " + ident + " ne postoji", designator);
            return Tab.noType;
        }

        if (obj.getKind() != Obj.Var && obj.getKind() != Obj.Con && obj.getKind() != Obj.Meth) {
            reportError("Identifikator " + ident + " se ne moze ovako koristiti", designator);
            return Tab.noType;
        }

        Struct struct = obj.getType();

        if (isArray && struct.getKind() != Struct.Array) {
            reportError("Identifikatora " + ident + " nije niz", designator);
            return Tab.noType;
        }

        return obj.getType();
    }

    public void visit(MethodCall methodCall) {
        methodCall.struct = methodCall.getDesignator().struct;
    }

    public void visit(DesignatorFctr factor) {
        factor.struct = factor.getDesignator().struct;
    }

    public void visit(ConstValueFctr factor) {
        factor.struct = factor.getConstValue().struct;
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
        if (term.getTerm().struct != Tab.intType || term.getFactor().struct != Tab.intType) {
            reportError("Operacija " + mulopToChar(term.getMulop()) + " se moze koristiti samo sa celobrojnim vrednostima", term);
            term.struct = Tab.noType;
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
        if (terms.getTerms().struct != Tab.intType || terms.getTerm().struct != Tab.intType) {
            reportError("Operacija " + addopToChar(terms.getAddop()) + " se moze koristiti samo sa celobrojnim vrednostima", terms);
            terms.struct = Tab.noType;
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
        if (expr.getTerm().struct != Tab.intType || expr.getTerms().struct != Tab.intType) {
            reportError("Operacija " + addopToChar(expr.getAddop()) + " se moze koristiti samo sa celobrojnim vrednostima", expr);
            expr.struct = Tab.noType;
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
