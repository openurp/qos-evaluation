[#ftl]
[@b.head/]
[@b.toolbar title="添加/修改列表"]
    bar.addBack();
[/@]

[@b.form name="indicatorForm" title="制定问题类别详细信息" action="!save" theme="list" ]
    [@b.textfield id="indicatorName" label="名称" required="true" check="maxLength(50)" name="indicator.name" value="${(indicator.name)?default('')}" maxlength="50"/]
    [@b.textfield label="英文名" check="maxLength(40)"name="indicator.enName" value="${(indicator.enName)?default('')}" maxlength="40"/]
    [@b.textfield label="问题优先级" required="true" check="match('integer', '问题优先级为非负整数')" name="indicator.priority" value="${(indicator.priority)?default(1)}" comment="<font color='red'>越高显示越靠前</font>" maxlength="5" /]
    [@b.startend label="生效日期" required="true,false" name="indicator.beginOn,indicator.endOn"   format="yyyy-MM-dd" start=indicator.beginOn! end=indicator.end!/]
    [@b.textarea label="备注"  check="maxLength(200)" name="indicator.remark" value="${(indicator.remark)?default('')}" style="width:500px"maxlength="200"/]
    [@b.formfoot]
        [#if indicator.persisted]
        <input name="indicator.id"  value="${indicator.id}" type="hidden"/>
        [/#if]

        [@b.submit value="action.submit"/]&nbsp;
        <input type="reset"  name="reset1" value="${b.text("action.reset")}" class="buttonStyle" />
    [/@]
[/@]

[@b.foot/]
