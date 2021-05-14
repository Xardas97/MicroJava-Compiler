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

        // TODO Check if return type is appropriate
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
        designator.struct = getDesignatorType(designator.getName(), designator);
    }

    public void visit(ArrayDesignator designator) {
        String ident = designator.getName();

        // TODO re-enable when Expr structs are set
        /*if (designator.getExpr().struct != Tab.intType) {
            reportError("Index identifikatora " + ident + " mora biti celobrojna vrednost", designator);
        }*/

        designator.struct = getDesignatorType(ident, designator);
    }

    private Struct getDesignatorType(String ident, Designator designator) {
        Obj obj = Tab.find(ident);

        if (obj == Tab.noObj) {
            reportError("Identifikator " + ident + " ne postoji", designator);
            return Tab.noType;
        }

        if (obj.getKind() != Obj.Var && obj.getKind() != Obj.Con && obj.getKind() != Obj.Meth) {
            reportError("Identifikator " + ident + " se ne moze ovako koristiti", designator);
            return Tab.noType;
        }

        return obj.getType();
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
