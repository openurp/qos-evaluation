<#include "/template/head.ftl"/>
<body>
    <table id="bar"></table>
    <#assign pageCaption = "课程质量评价学院汇总历史对比"/>
    <#assign tdCount = 4 + grades?size * 4/>
    <#assign tdWidth = 50/>
    <table width="${tdWidth * tdCount}px" align="center">
        <tr>
            <td style="font-size:13.5pt;font-weight:bold;text-align:center">${pageCaption}<br>（${department.name}）</td>
        </tr>
        <tr>
            <td style="font-size:10pt;"><#list grades as item>${item.name}：<#if item_index == 0>${item.min}分以上<#elseif item_index == criteriaItems?size - 1>${item.max}分以下<#else>${item.min}－${item.max - 0.1}</#if><#if item_has_next>；</#if></#list></td>
        </tr>
    </table>
    <table class="listTable" width="${tdWidth * tdCount}px" style="text-align:center" align="center">
        <tr>
            <td width="${tdWidth * 2}px">评价时间</td>
            <td colspan="2">平均分</td>
            <#list grades as item>
            <td colspan="2">${item.name}（人次）</td>
            <td colspan="2">所占比例（%）</td>
            </#list>
        </tr>
        <tr>
            <td></td>
            <#list 0..(grades?size * 2) as i>
            <td width="${tdWidth}px">学院</td>
            <td width="${tdWidth}px">学校</td>
            </#list>
        </tr>
        <#list semesterDepartmentResults as result>
        <tr>
            <td>${result[0].semester.schoolYear}（${result[0].semester.name}）</td>
            <td>${result[0].score?string("0.00")}</td>
            <td>${semesterCollegeResults[result_index][0].score?string("0.00")}</td>
            <#assign departTotal = 0/>
            <#assign collegeTotal = 0/>
            <#list 1..grades?size as i>
                <#assign departTotal = departTotal + (result[i])?default(0)/>
                <#assign collegeTotal = collegeTotal + (semesterCollegeResults[result_index][i])?default(0)/>
            </#list>
            <#list 1..grades?size as i>
            <td>${(result[i])?default(0)}</td>
            <td>${(semesterCollegeResults[result_index][i])?default(0)}</td>
            <td>${((result[i])?default(0) / departTotal * 100)?string("0.00")}</td>
            <td>${((semesterCollegeResults[result_index][i])?default(0) / collegeTotal * 100)?string("0.00")}</td>
            </#list>
        </tr>
        </#list>
    </table>
    <script>
        var bar = new ToolBar("bar", "${pageCaption}", null, true, true);
        bar.setMessage('<@getMessage/>');
        bar.addPrint("<@text name="action.print"/>");
        bar.addBackOrClose("<@text name="action.back"/>", "<@text name="action.close"/>");
    </script>
</body>
<#include "/template/foot.ftl"/>
