[#ftl]
[@b.head/]
[@b.toolbar title="问题列表"]
    bar.addItem("添加","addContexts()")
[/@]
[@b.grid items=questions var="question" sortable="false"]
    [@b.row]
        [@b.boxcol/]
        [@b.col property="indicator.name" title="问题类型"/]
        [@b.col property="contents" title="问题内容"/]
        [@b.col property="depart.name" title="创建部门"/]
        [@b.col property="score" title="问题分值"]${question.score?default(0)?string("###0.0")}[/@]
    [/@]
[/@]
<script language="JavaScript">
    var questionArray= new Array();
    [#list questions?if_exists as question]
        questionArray[${question_index}]=new Array();
        questionArray[${question_index}][0]='${question.id!}';
        questionArray[${question_index}][1]="${(question.indicator.name?html)!}";
        questionArray[${question_index}][2]="${(question.contents?html?js_string)!}";
        questionArray[${question_index}][3]='${question.indicator.id!}';
    [/#list]

    function addContexts(){
        var idSeq = bg.input.getCheckBoxValues("question.id");
        if(""==idSeq || null==idSeq){
            alert("请选择一些问题");
            return;
          }
        var ids = idSeq.split(",");
        for(var j=0;j<ids.length;j++){
            for(var i=0;i<questionArray.length;i++){
                if(questionArray[i][0]==ids[j]){
                    opener.addContext(questionArray[i]);
                    break;
                }
              }
          }
         window.close();
    }
</script>
[@b.foot/]
