package pt.up.fe.comp.analysis.analyser;

import pt.up.fe.comp.analysis.SymbolTableBuilder;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.List;

public class ObjectAssignmentCheck extends PreorderJmmVisitor<Integer, Integer>  {

    private final SymbolTableBuilder symbolTable;
    private final List<Report> reports;

    public ObjectAssignmentCheck(SymbolTableBuilder symbolTable, List<Report> reports) {

        this.reports = reports;
        this.symbolTable = symbolTable;
        addVisit("Assignment", this::visitObjectAssignmentFail);
        setDefaultVisit((node, oi) -> 0);
    }

    public Integer visitObjectAssignmentFail(JmmNode assignmentNode, Integer ret) {
        JmmNode left_node = assignmentNode.getJmmChild(0);
        JmmNode right_node = assignmentNode.getJmmChild(1);
        String method_name = null;
        String left_node_name = null;
        if( assignmentNode.getAncestor("MethodDeclaration").get().getJmmChild(0).getKind().equals("MainMethodHeader")) method_name = "main";
        else method_name = assignmentNode.getAncestor("MethodDeclaration").get().getJmmChild(0).getJmmChild(1).get("name");


        if(symbolTable.isObject(method_name, left_node_name) && right_node.getKind().equals("Identifier")) {
            String right_node_name = right_node.get("name");
            if(symbolTable.isObject(method_name, right_node_name)){
                if(symbolTable.getImports().contains(symbolTable.getVariableType(method_name,right_node_name).getName()) && symbolTable.getImports().contains(symbolTable.getVariableType(method_name,left_node_name).getName())) {
                    return 1;
                }
                else if(symbolTable.getSuper() == null){
                    reports.add(Report.newError(Stage.SEMANTIC, -1, -1, "\"" + left_node_name + "\" invalid assignment: doens't extend " + "\"" + right_node_name + "\"", null));
                }
                else if(symbolTable.getSuper().equals(right_node_name)){
                    reports.add(Report.newError(Stage.SEMANTIC, -1, -1, "\"" + right_node_name + "\" invalid assignment: can't assign to father class", null));
                }
                else if(symbolTable.getSuper().equals(left_node_name)){
                    return 1;
                }
            }
        }
        return 0;
    }

    public List<Report> getReports(){
        return reports;
    }
}