package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {
    boolean errorDetected = false;
    int nVars;

    private Struct boolType;
    private Logger log = Logger.getLogger(getClass());

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
            type.struct = Tab.noType;
            return;
        }

        if (typeObj.getKind() != Obj.Type) {
            reportError("Identifikator nije tip: " + type.getTypeName(), type);
            type.struct = Tab.noType;
            return;
        }

        type.struct = typeObj.getType();
    }

    public void visit(MethodTypeName methodTypeName) {
        Struct returnType = Tab.noType;
        if (methodTypeName.getReturnType() instanceof NonVoidReturnType) {
            returnType = ((NonVoidReturnType)methodTypeName.getReturnType()).getType().struct;
        }

        methodTypeName.obj = Tab.insert(Obj.Meth, methodTypeName.getMethodName(), returnType);
        Tab.openScope();

        reportInfo("Obradjuje se funkcija: " + methodTypeName.getMethodName(), methodTypeName);
    }

    public void visit(MethodDecl methodDecl) {
        Tab.chainLocalSymbols(methodDecl.getMethodTypeName().obj);
        Tab.closeScope();
    }

    private void reportError(String message, SyntaxNode info) {
        errorDetected = true;

        StringBuilder msg = new StringBuilder(message);
        if (info != null) {
            msg.append(" na liniji: ").append(info.getLine());
        }

        log.info(msg.toString());
    }

    private void reportInfo(String message, SyntaxNode info) {
        StringBuilder msg = new StringBuilder(message);
        if (info != null) {
            msg.append(" na liniji: ").append(info.getLine());
        }

        log.info(msg.toString());
    }
}
