package pt.up.fe.comp.ollir;
import pt.up.fe.comp.*;
import pt.up.fe.comp.ast.ASTNode;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.lang.reflect.Method;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OllirGenerator extends AJmmVisitor<Integer, String>{

    static int varCounter;
    static int markerCounter;
    private final StringBuilder code;
    private final SymbolTable symbolTable;

    public OllirGenerator(SymbolTable symbolTable){
        this.varCounter = 0;
        this.code = new StringBuilder();
        this.symbolTable = symbolTable;

        addVisit(ASTNode.START, this::programVisit);
        addVisit(ASTNode.IMPORT_DECLARATION, this::importDeclVisit);
        addVisit(ASTNode.VAR_DECLARATION, this::varDeclVisit);
        addVisit(ASTNode.CLASS_DECLARATION, this::classDeclVisit);
        addVisit(ASTNode.METHOD_DECLARATION, this::methodDeclVisit);
        addVisit(ASTNode.DOT_ACCESS, this::memberCallVisit);
        addVisit(ASTNode.ARGUMENTS, this::argumentsVisit);
        addVisit(ASTNode.IDENTIFIER, this::idVisit);
        addVisit(ASTNode.NUMBER, this::numberVisit);
        addVisit(ASTNode.TRUE, this::boolVisit);
        addVisit(ASTNode.FALSE, this::boolVisit);
        addVisit(ASTNode.EXPRESSION_STATEMENT, this::operationVisit);
        addVisit(ASTNode.EXPRESSION, this::operationVisit);
        addVisit(ASTNode.AND, this::operationVisit);
        addVisit(ASTNode.LESS, this::operationVisit);
        addVisit(ASTNode.PLUS, this::operationVisit);
        addVisit(ASTNode.MINUS, this::operationVisit);
        addVisit(ASTNode.TIMES, this::operationVisit);
        addVisit(ASTNode.DIVIDE, this::operationVisit);
        addVisit(ASTNode.NOT, this::operationVisit);
        addVisit(ASTNode.NEW_DECLARATION, this::newVisit);
        addVisit(ASTNode.IF_STATEMENT, this::ifVisit);
        addVisit(ASTNode.ELSE_SCOPE, this::elseVisit);
        addVisit(ASTNode.WHILE_STATEMENT, this::whileVisit);
        addVisit(ASTNode.ASSIGNMENT, this::assignmentVisit);
        addVisit(ASTNode.RETURN, this::returnVisit);
        addVisit(ASTNode.ARRAY_ACCESS, this::arrayAccessVisit);
        //addVisit(ASTNode.VAR_ACCESS, this::varAccessVisit);
        setDefaultVisit(this::defaultVisit);


        //addVisit("VAR_DECLARATION", this::dealWithVar);

        //addVisit("ASSIGNMENT", this::dealWithAssignment);
        //addVisit("IF", this::dealWithIf);
        //addVisit("ELSE", this::dealWithElse);
        //addVisit("WHILE", this::dealWithWhile);
        //addVisit("ARRAY_ACCESS", this::dealWithArrayAccess);
        //setDefaultVisit(this::defaultVisit);
    }

    public String getCode(){
        return code.toString();
    }

    private String programVisit(JmmNode program, Integer dummy){

        //System.out.println(code);

        for (var child: program.getChildren())
            code.append(visit(child));

        return code.toString();
    }
    private String importDeclVisit(JmmNode importDecl, Integer dummy) {
        StringBuilder importString = new StringBuilder();
        for (var importStmt : symbolTable.getImports())
            importString.append("import ").append(importStmt).append(";\n");

        return importString.toString();
    }

    private String classDeclVisit(JmmNode classDecl, Integer dummy){
        StringBuilder classString = new StringBuilder();

        classString.append(symbolTable.getClassName());

        var superClass = symbolTable.getSuper();
        if (superClass != null)
            classString.append(" extends ").append(superClass);

        classString.append(" {\n");

        if (classDecl.getJmmChild(1).getKind().equals("VarDeclaration")) visit(classDecl.getJmmChild(1));

        classString.append(".construct ").append(symbolTable.getClassName()).append("().V{\ninvokespecial(this, \"<init>\").V;\n}\n\n");

        for (var child: classDecl.getChildren())
            if (!child.getKind().equals("Identifier") && !child.getKind().equals("VarDeclaration") ) classString.append(visit(child));


        classString.append("}\n");

        return classString.toString();
    }

    private String varDeclVisit(JmmNode varDecl, Integer dummy){
        StringBuilder varStr = new StringBuilder();
        JmmNode parent = varDecl.getJmmParent();
        JmmNode type = varDecl.getJmmChild(0);
        JmmNode identifier = varDecl.getJmmChild(1);

        if (parent.getKind().equals("ClassDeclaration")) {
            varStr.append(".field private ");
            varStr.append(visit(identifier)).append(".");
            varStr.append(OllirUtils.getOllirType(getFieldType(identifier.get("name"))));
            varStr.append(";\n");
        }

        return varStr.toString();
    }

    private String methodDeclVisit(JmmNode methodDecl, Integer dummy){
        StringBuilder methodString = new StringBuilder();
        var methodType = methodDecl.getJmmChild(0);
        List<String> methods = symbolTable.getMethods();
        List<Symbol> params;
        boolean isMain = methodType.getKind().equals("MainMethodHeader");

        methodString.append(" .method public ");
        if (isMain){
            methodString.append("static main(");
            params = symbolTable.getParameters("main");
        }
        else {
            methodString.append(methodDecl.getJmmChild(0).getJmmChild(1).get("name")).append("(");
            params = symbolTable.getParameters(methodName(methodDecl));
        }

        String paramCode = params.stream().map(symbol -> OllirUtils.getCode(symbol)).collect(Collectors.joining(", "));

        for (int i = 1; i <= params.size() + 1; i++)
            paramCode.replace("$" + i + ".", "");
        methodString.append(paramCode);

        methodString.append(")");

        methodString.append(OllirUtils.getOllirType(symbolTable.getReturnType(methodName(methodDecl))));
        methodString.append(" {\n");


        for (var stmt: methodDecl.getJmmChild(1).getChildren())
            methodString.append(visit(stmt));

        if(!methodString.toString().contains("ret."))
            methodString.append("ret.V;\n");



        methodString.append("}\n");


        return methodString.toString();
    }


    private String ifVisit(JmmNode ifStmt, Integer dummy){
        StringBuilder ifStr = new StringBuilder();
        JmmNode conditionStmt = ifStmt.getJmmChild(0);
        JmmNode bodyBlock = ifStmt.getJmmChild(1);
        JmmNode elseBlock = ifStmt.getJmmChild(2);

        String condStr = visit(conditionStmt);

        int localCounter = ++markerCounter;


        String prefix = "";

        if (condStr.contains("\n")){
            prefix = condStr.substring(0, condStr.lastIndexOf("\n") + 1);
            condStr = condStr.substring(condStr.lastIndexOf("\n") + 1);
        }

        String newCondStr = this.buildCondition(conditionStmt, condStr, false);

        ifStr.append(prefix);

        ifStr.append("\t\tif (").append(newCondStr).append(") goto Else").append(localCounter).append(";\n");

        for (JmmNode bodyStmt : bodyBlock.getChildren())
            ifStr.append("\t").append(visit(bodyStmt));


        ifStr.append("\t\tgoto EndIf").append(localCounter).append(";\n");

        ifStr.append("\t").append(visit(elseBlock));

        return ifStr.toString();
    }
    private String elseVisit(JmmNode jmmNode, Integer dummy) {
        //int localLabel = ifCount;
        StringBuilder elseStr = new StringBuilder();
        int localCounter = markerCounter;
        elseStr.append("\t\tElse" + localCounter + ":\n");

        for (JmmNode child : jmmNode.getChildren()) {
            elseStr.append("\t").append(visit(child));
        }
        elseStr.append("\t\tEndIf").append(localCounter).append(":\n");
        //ifCount--;
        return elseStr.toString();
    }

    private String whileVisit(JmmNode whileStmt, Integer dummy){
        StringBuilder whileStr = new StringBuilder();
        JmmNode conditionStmt = whileStmt.getChildren().get(0);
        String condStr = visit(conditionStmt);
        int localCounter = ++markerCounter;
        String prefix = "";

        if (condStr.contains("\n")){
            prefix = condStr.substring(0, condStr.lastIndexOf("\n") + 1);
            condStr = condStr.substring(condStr.lastIndexOf("\n") + 1);
        }

        String newCondStr = this.buildCondition(conditionStmt, condStr, true);

        whileStr.append("\t\tLoop" + localCounter + ":\n");
        whileStr.append(prefix);

        whileStr.append("\t\t\tif (").append(newCondStr).append(") goto EndLoop").append(localCounter);
        whileStr.append(";\n");

        for (int i = 1; i < whileStmt.getChildren().size(); i++)
            whileStr.append(visit(whileStmt.getJmmChild(i)));

        whileStr.append("\t\tgoto Loop").append(localCounter).append(";\n\t\tEndLoop").append(localCounter).append(":\n");


        return whileStr.toString();
    }

    private String assignmentVisit(JmmNode assignmentStmt, Integer dummy){
        StringBuilder methodStr = new StringBuilder();
        JmmNode identifier = assignmentStmt.getJmmChild(0);
        JmmNode assignment = assignmentStmt.getJmmChild(1);
        JmmNode parent = assignmentStmt.getJmmParent();
        String type = "";

        if (identifier.getKind().equals("ArrayAccess"))
            type = getVarType(identifier.getJmmChild(0).get("name")).replace(".array", "");
        else type = getVarType(identifier.get("name"));


        String idStr = visit(identifier);
        String assignmentStr = visit(assignment);
        int currentCount = varCounter;

        List<Symbol> fields = symbolTable.getFields();
        Symbol var = null;
        boolean isField = false;

        JmmNode child = identifier.getChildren().size() == 0 ? identifier : identifier.getJmmChild(0);

        for (Symbol symbol : fields) {
            if (symbol.getName().equals(child.get("name"))) {
                isField = true;
                break;
            }
        }

        if (idStr.contains("\n")){
            String prefix = idStr.substring(0, idStr.lastIndexOf("\n") + 1);
            idStr = idStr.substring(idStr.lastIndexOf("\n") + 1);
            if (idStr.contains("."))
                idStr = idStr.substring(0, idStr.lastIndexOf("."));
            methodStr.append(prefix);
        }

        if (!isField) {

            if (assignmentStr.contains("\n")) {
                String prefix = assignmentStr.substring(0, assignmentStr.lastIndexOf("\n") + 1);
                assignmentStr = assignmentStr.substring(assignmentStr.lastIndexOf("\n") + 1);
                methodStr.append(prefix);
            }

            if (assignment.getKind().equals("Number"))
                assignmentStr += ".i32"; //todo check if needed

            methodStr.append(idStr).append(type).append(" :=").append(type).append(" ").append(assignmentStr); // typecheck

            methodStr.append(";\n");

        } else{
            if (assignmentStr.contains("\n")) {
                String prefix = assignmentStr.substring(0, assignmentStr.lastIndexOf("\n") + 1);
                assignmentStr = assignmentStr.substring(assignmentStr.lastIndexOf("\n") + 1);
                methodStr.append(prefix);
            }else if (!assignment.getKind().equals("Number") && !assignment.getKind().equals("Identifier")){
                methodStr.append("t").append(++varCounter).append(type).append(" :=").append(type).append(" ");
                methodStr.append(assignmentStr).append(";\n");
                assignmentStr = "t" + varCounter + type;
            }


            if (!assignmentStr.contains(".")) assignmentStr += ".i32"; // number fix, todo may need type change

            String fieldId = "";
            if (identifier.getKind().equals("ArrayAccess"))
                fieldId = identifier.getJmmChild(0).get("name");
            else fieldId = identifier.get("name");


            methodStr.append("putfield(this, ").append(fieldId).append(type).append(", ").append(assignmentStr).append(").V;\n");
        }

        return methodStr.toString();
    }

    private String varAccessVisit(JmmNode varAccess, Integer dummy) {
        StringBuilder varStr = new StringBuilder();

        List<Symbol> fields = symbolTable.getFields();
        List<String> methods = symbolTable.getMethods();

        JmmNode method = varAccess.getJmmParent().getJmmParent();

        JmmNode id = varAccess.getJmmChild(0);
        JmmNode array = varAccess.getJmmChild(1);

        Symbol var = null;
        boolean isField = false;
        for (Symbol symbol : fields) {
            if (symbol.getName().equals(id.get("name"))) {
                isField = true;
                var = symbol;
                break;
            }
        }
        if (!isField){
            List<Symbol> localVars = symbolTable.getLocalVariables(method.get("name"));
            for (Symbol localVar:localVars){
                if (localVar.getName().equals(id.get("name"))){
                    var = localVar;
                    break;
                }
            }
        }
        //putfield(this, a.i32, $1.n.i32).V;
        if (isField){
            varStr.append("putfield(this, ").append(var.getName())
                    .append(OllirUtils.getOllirType(var.getType()))
                    .append(", "); // TODO ON THE ASSIGNMENT CHECK IF FIELD AND PUT THE REST
            return var.toString();
        }

        if (array == null){
            varStr.append(var.getName()).append(".").append(OllirUtils.getOllirType(var.getType()))
                    .append(" :=").append(OllirUtils.getOllirType(var.getType())).append(" ");
        }
        else{
            varStr.append(visit(array));
            varStr.append("t").append(varCounter).append(".").append(OllirUtils.getOllirType(var.getType()))
                    .append(" :=").append(OllirUtils.getOllirType(var.getType())).append(" ");
        }



        return varStr.toString();

    }

    public String arrayAccessVisit(JmmNode arrayAccess, Integer dummy){
        StringBuilder retStr = new StringBuilder();
        JmmNode identifier = arrayAccess.getChildren().get(0);
        JmmNode indexNode = arrayAccess.getChildren().get(1);

        String idStr = visit(identifier);
        String indexStr = visit(indexNode);

        if (idStr.contains("\n")){
            retStr.append(idStr.substring(0, idStr.lastIndexOf("\n") + 1));
            idStr = idStr.substring(idStr.lastIndexOf("\n") + 1);
        }

        if (indexStr.contains("\n")){
            retStr.append(indexStr.substring(0, indexStr.lastIndexOf("\n") + 1));
            indexStr = indexStr.substring(indexStr.lastIndexOf("\n") + 1);
        }

        if (indexNode.getKind().equals("Number"))
            indexStr += ".i32"; //todo check if needed

        String type = getVarType(idStr).replace(".array", "");

        retStr.append("\t\tt").append(++varCounter).append(type);
        retStr.append(" :=").append(type).append(" ");
        retStr.append(idStr).append("[").append(indexStr).append(type).append("]").append(type);
        retStr.append(";\nt").append(varCounter).append(type);//typecheck

        return retStr.toString();
    }

    private String memberCallVisit(JmmNode memberCall, Integer dummy){
        StringBuilder methodString = new StringBuilder();
        JmmNode id = memberCall.getJmmChild(0);
        JmmNode func = memberCall.getJmmChild(1).getJmmChild(0);
        JmmNode args;
        String argPrefix = "";
        String argString = "";
        if (!func.getKind().equals("Length")){
            args = memberCall.getJmmChild(1).getJmmChild(1);

            for (JmmNode child : args.getChildren()){
                String node = visit(child);
                if (node.contains("\n")){
                    argPrefix += node.substring(0, node.lastIndexOf("\n") + 1);
                    argString += (", " + node.substring(node.lastIndexOf("\n") + 1));
                }
                else argString += (node + getVarType(child.get("name")));
            }

            methodString.append(argPrefix);
        }



        if (func.getKind().equals("Length")) {
            methodString.append("t").append(++varCounter).append(".i32 :=.i32 arraylength(").append(visit(id)).append(getVarType(id.get("name"))).append(").i32;\n"); //todo check type
            methodString.append("t").append(varCounter).append(".i32");
        }else if (id.getKind().equals("This")){
            String type = OllirUtils.getOllirType(symbolTable.getReturnType(func.get("name"))); //typecheck

            if (!type.equals(".V"))
                methodString.append("t").append(++varCounter).append(type).append(" :=").append(type).append(" ");

            methodString.append("invokevirtual(").append("this");

            methodString.append(", \"").append(visit(func)).append("\"");

            methodString.append(argString);

            methodString.append(")");

            methodString.append(type).append(";\n");

            if(!type.equals(".V"))
                methodString.append("t").append(varCounter).append(type);

        }
        else if (id.getKind().equals("Identifier")) {
            List<String> imports = symbolTable.getImports();

            for (String importStr: imports){
                if (importStr.equals(id.get("name"))){
                    methodString.append("invokestatic(").append(id.get("name"));

                    methodString.append(", \"").append(visit(func)).append("\"");

                    methodString.append(argString);
                    methodString.append(").V;\n");

                    return methodString.toString();
                }
            }

            // else identifier is not imported
            String type = ".V";

            if (symbolTable.getClassName().equals(getVarType(id.get("name"))))
            for (String method : symbolTable.getMethods())
                if (method.equals(func.get("name"))) type = OllirUtils.getOllirType(symbolTable.getReturnType(func.get("name")));

            if (!type.equals(".V"))
                methodString.append("t").append(++varCounter).append(type).append(" :=").append(type).append(" ");

            methodString.append("invokevirtual(");
            methodString.append(visit(id)).append(getVarType(id.get("name"))); //typecheck

            methodString.append(", \"").append(visit(func)).append("\"");

            methodString.append(argString);
            methodString.append(")");

            methodString.append(type).append(";\n");

            if(!type.equals(".V"))
                methodString.append("t").append(varCounter);


        } else if (id.getKind().equals("NewDeclaration")) {
            String type = getVarType(id.getJmmChild(0).get("name"));
            String prefix = "t" + ++varCounter + type + ":=" + type + " " + visit(id) + ";\n";
            String postfix = "t" + varCounter + type;

            methodString.append(prefix);
            methodString.append("invokespecial(").append(postfix).append(", \"<init>\").V\n");

        } else {
            System.out.println("Default(Other type)");

        }


        return methodString.toString();
    }

    private String argumentsVisit(JmmNode arguments, Integer dummy){
        StringBuilder argsString = new StringBuilder();
        for (var child: arguments.getChildren())
            argsString.append(", ").append(visit(child));

        return argsString.toString();
    }

    private String returnVisit(JmmNode returnNode, Integer dummy){
        StringBuilder returnString = new StringBuilder();
        JmmNode expression = returnNode.getChildren().get(0);
        String exp = visit(expression);
        String type = OllirUtils.getOllirType(symbolTable.getReturnType(methodName(returnNode)));

        if (exp.contains("\n")){
            returnString.append(exp.substring(0, exp.lastIndexOf("\n") + 1));
            String tmp = exp.substring(exp.lastIndexOf("\n") + 1);
            returnString.append("ret").append(type);
            returnString.append(tmp).append(type);
        }
        else {
            returnString.append("ret").append(type);
            returnString.append(" ").append(exp).append(type);
        }
        returnString.append(";\n");
        return returnString.toString();
    }


    private String idVisit(JmmNode id, Integer dummy){
        List<Symbol> fields = symbolTable.getFields();
        for (Symbol symbol : fields) {
            if (symbol.getName().equals(id.get("name"))) {
                return "t" + ++varCounter + OllirUtils.getOllirType(getFieldType(id.get("name"))) + " :="
                        + OllirUtils.getOllirType(getFieldType(id.get("name"))) + " " + "getfield(this, " + id.get("name")
                        + OllirUtils.getOllirType(getFieldType(id.get("name"))) + ");\nt" + varCounter
                        + OllirUtils.getOllirType(getFieldType(id.get("name"))); //typecheck
            }
        }

        String method = methodName(id);
        if (!method.equals("")){
            List<Symbol> params = symbolTable.getParameters(method);
            for (int i = 0; i < params.size(); i++){
                if (params.get(i).getName().equals(id.get("name")))
                    return "$" + (i + 1) + "." + id.get("name");
            }
        }

        // extern functions don't print id
        List<JmmNode> children = id.getJmmParent().getChildren();
        List<String> imports = symbolTable.getImports();

        for (int i = 0; i < imports.size(); i++)
            if (imports.get(i).equals(id.get("name"))) // id está nos imports
                for (int j = 0; j < children.size(); j++)
                    if (children.get(j).getKind().equals("Identifier") && children.get(j).get("name").equals(imports.get(i))) // encontrar posição do id no pai
                        if (j < children.size() - 1 && children.get(j + 1).getKind().equals("DotAccess")) // id seguido de dot access
                            return "";

        return id.get("name");
    }

    private String numberVisit(JmmNode value, Integer dummy){
        return value.get("name");
    }

    private String boolVisit(JmmNode value, Integer dummy){
        if (value.getKind().equals("True")) return "true";
        return "false";
    }

    private String newVisit(JmmNode newNode, Integer dummy){

        StringBuilder newString = new StringBuilder();
        JmmNode child = newNode.getJmmChild(0);
        if (!child.getKind().equals("Identifier")) {
            newString.append("new(array, ");
            newString.append(visit(child.getJmmChild(1)));
            newString.append(".i32).array.i32");
        } else newString.append("new(").append(child.get("name")).append(").").append(child.get("name")); //typecheck

        return newString.toString();
    }

    private String parseOp(String op){
        if (op.equals("And") || op.equals("Less") || op.equals("Not")) return "bool";
        return "int";
    }

    private String defaultVisit(JmmNode defaultNode, Integer dummy) {
        StringBuilder visitStr = new StringBuilder();
        for (JmmNode child : defaultNode.getChildren()) {
            visitStr.append(visit(child, dummy));
        }
        return visitStr.toString();
    }

    private String getOperator(JmmNode jmmNode) {
        if (jmmNode.getKind().equals("Plus"))
            return " +.i32 ";
        if (jmmNode.getKind().equals("Minus"))
            return " -.i32 ";
        if (jmmNode.getKind().equals("Times"))
            return " *.i32 ";
        if (jmmNode.getKind().equals("Divide"))
            return " /.i32 ";
        if (jmmNode.getKind().equals("Less"))
            return " <.bool ";
        if (jmmNode.getKind().equals("And"))
            return " &&.bool ";
        if (jmmNode.getKind().equals("Not"))
            return " !.bool ";
        return " .V ";
    }

    private String operationVisit(JmmNode operation, Integer dummy) {
        StringBuilder operationStr = new StringBuilder();
        String leftNewVar = "";
        String rightNewVar = "";
        String opType = "";
        if(operation.getKind().equals("Plus") || operation.getKind().equals("Minus") ||
                operation.getKind().equals("Times") || operation.getKind().equals("Divide") || operation.getKind().equals("Less"))
            opType = ".i32";
        else opType = ".bool";

        boolean isNot = operation.getKind().equals("Not"); //todo not



        JmmNode left = operation.getJmmChild(0);
        JmmNode right = (!isNot)? operation.getChildren().get(1) : null;

        String leftStr = visit(left);
        String rightStr = (!isNot)? visit(right) : "";

        if (leftStr.contains("\n")){
            String leftPrefix = leftStr.substring(0, leftStr.lastIndexOf("\n") + 1);
            operationStr.append(leftPrefix).append("\n");
            leftNewVar = leftStr.substring(leftStr.lastIndexOf("\n") + 1);
        }

        // TODO CHANGE TYPES
        //  1 + 2 && 3;

        //  t1.32 and 3.i32
        //      1 ADD 2        t1.i32 :=.i32 (1.i32 +.i32 2.i32 \n t1

        if ((!isNot) && rightStr.contains("\n")){
            String rightPrefix = rightStr.substring(0, rightStr.lastIndexOf("\n") + 1);
            operationStr.append(rightPrefix).append("\n");
            rightNewVar = rightStr.substring(rightStr.lastIndexOf("\n") + 1);
        }


        if (leftNewVar.equals("") && rightNewVar.equals("") && OllirUtils.isOperation(operation.getJmmParent()))
            operationStr.append("\nt").append(++varCounter).append(opType).append(" :=").append(opType).append(" ");


        if (leftNewVar.equals("")) operationStr.append(leftStr).append(opType);
        else operationStr.append(leftNewVar);

        operationStr.append(getOperator(operation));

        if (rightNewVar.equals("")) operationStr.append(rightStr).append(opType);
        else operationStr.append(rightNewVar);


        if (leftNewVar.equals("") && rightNewVar.equals("") && OllirUtils.isOperation(operation.getJmmParent()))
            operationStr.append(";\nt").append(varCounter).append(opType);
//
        return operationStr.toString();
    }

    private String buildCondition(JmmNode condition, String condStr, boolean isWhile) {
        StringBuilder finalStr = new StringBuilder();

        if (condition.getKind().equals("DotAccess") || condition.getKind().equals("ArrayAccess")) {
            finalStr.append("t").append(++varCounter).append(".bool :=.bool ").append(condStr).append(";\n");
            finalStr.append("t").append(varCounter);
            //if (isWhile)
            //    finalStr.append(".bool ==.bool 1.bool");
            //else
            finalStr.append(".bool !.bool 1.bool");


        } else if (condition.getKind().equals("True") || condition.getKind().equals("False") || condition.getKind().equals("Identifier")) {

            //if (isWhile)
            //    finalStr.append(condStr).append(".bool ==.bool ").append(condStr).append(".bool");
            //else
            finalStr.append(condStr).append(".bool !.bool ").append("1.bool");
        }
        else{
            // if (isWhile)
            //     return condStr;
            // else{
            if (condStr.contains("<")) {
                finalStr.append(condStr);
                finalStr.replace(finalStr.indexOf("<"), finalStr.indexOf("<") + 1, ">=");
            }
            else if (condStr.contains("==")) {
                finalStr.append(condStr);
                finalStr.replace(finalStr.indexOf("=="), finalStr.indexOf("==") + 2, "!");
            }
            else if (condStr.contains("!")) {
                finalStr.append(condStr);
                finalStr.replace(finalStr.indexOf("!"), finalStr.indexOf("!") + 2, "==");
            }
            //todo && case
            //}
        }


        return finalStr.toString();
    }

    private Type getFieldType(String name){
        for (Symbol symbol: symbolTable.getFields()){
            if (name.equals(symbol.getName()))
                return symbol.getType();
        }
        return null;
    }


    private String getVarType(String name) {
        for (Symbol symbol : symbolTable.getFields())
            if (name.equals(symbol.getName()))
                return OllirUtils.getOllirType(symbol.getType());

        for (String method : symbolTable.getMethods()) {
            for (Symbol symbol : symbolTable.getParameters(method))
                if (name.equals(symbol.getName()))
                    return OllirUtils.getOllirType(symbol.getType());

            for (Symbol symbol : symbolTable.getLocalVariables(method))
                if (name.equals(symbol.getName()))
                    return OllirUtils.getOllirType(symbol.getType());
        }

        return ".i32";
    }

    public String methodName(JmmNode nodeId){
        while(!nodeId.getKind().equals("MethodDeclaration") && !nodeId.getKind().equals("Start")){
            nodeId = nodeId.getJmmParent();
        }

        if (nodeId.getKind().equals("Start")) return "";
        else {
            if (nodeId.getJmmChild(0).getKind().equals("MainMethodHeader")) return "main";
            else return nodeId.getJmmChild(0).getJmmChild(1).get("name");
        }
    }


};


