/*
 * Copyright (C) 2014, The OpenURP Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openurp.qos.evaluation.util

import com.google.gson.Gson
import org.beangle.commons.net.http.{HttpMethods, HttpUtils}

import java.io.{File, FileWriter}
import java.net.URL
import java.nio.charset.Charset
import java.util as ju

object ConvertApi2SQL {
  def main(args: Array[String]): Unit = {
    fetchFinal("202320242")
    //fetchOpenFeedback("202320241")
  }

  def fetchOpenFeedback(semesterNo: String): Unit = {
    //create table qos.evaluate_data(semester_no varchar(20),clazz_no varchar(4),grade varchar(4),teacher_no varchar(15),category_rank integer,depart_rank integer,course_no varchar(20),score real,tickets integer,teach_depart_no varchar(10),teacher_depart_no varchar(10));
    //create table qos.evaluate_data2(semester_no varchar(20),clazz_no varchar(4),teacher_no varchar(15),score real,rank integer,indicator_name varchar(30),remark varchar(100));
    //create table qos.evaluate_data3(id bigint,semester_no varchar(20),clazz_no varchar(4),teacher_no varchar(15),course_no varchar(20),teach_depart_no varchar(20),std_no varchar(20),conclusion varchar(100),details varchar(1000),created_at varchar(30));
    val gson = new Gson()
    val utf8 = Charset.forName("UTF-8")
    val url = s"https://pjxt.ecupl.edu.cn/api/open/result/index?semester_no=${semesterNo}&limit=4000"
    val content = HttpUtils.getText(new URL(url), HttpMethods.GET, utf8, Some({ c =>
      c.setConnectTimeout(60 * 1000)
      c.setReadTimeout(60 * 1000)
    })).getText
    val a = gson.fromJson(content, classOf[ju.Map[String, Object]])
    val list = get[ju.List[ju.Map[String, Object]]](a, "data.list")
    val listIter = list.iterator()
    val data3File = new FileWriter(new File(s"./data${semesterNo}.sql"), utf8)

    var i = 0
    while (listIter.hasNext) {
      i += 1
      val clazzData = listIter.next();
      val clazzNo = get[String](clazzData, "clazz_no")
      println(s"process ${i} ${clazzNo}")
      val crn = clazzNo.substring(9)
      val grade = get[String](clazzData, "grade")
      val teacherNo = get[String](clazzData, "teacher.job_no")
      val teachDepartNo = get[String](clazzData, "dept_no")
      val teacherDepartNo = get[String](clazzData, "teacher.dept_no")
      val subjectRank = get[Number](clazzData, "subject.rank").intValue()
      val departRank = get[Number](clazzData, "college.rank").intValue()
      val courseNo = get[String](clazzData, "course.course_no")

      val feedbackUrl = s"https://pjxt.ecupl.edu.cn/api/open/feedback/index?type=1&semester_no=${semesterNo}&limit=4000&clazz_no=${clazzNo}&teacher_no=${teacherNo}"
      val feedbackContent = HttpUtils.getText(new URL(feedbackUrl), HttpMethods.GET, utf8, Some({ c =>
        c.setConnectTimeout(60 * 1000);
        c.setReadTimeout(60 * 1000)
      })).getText
      val feedbackData = gson.fromJson(feedbackContent, classOf[ju.Map[String, Object]])
      val feedbacks = get[ju.List[ju.Map[String, Object]]](feedbackData, "data.list")
      val feedbackIter = feedbacks.iterator()
      while (feedbackIter.hasNext) {
        val feedback = feedbackIter.next()
        val studentNo = get[String](feedback, "student_no")
        val createdAt = get[String](feedback, "created_at")
        val id = get[Number](feedback, "id").longValue()
        val answers = get[ju.List[ju.Map[String, Object]]](feedback, "answers")
        val answerIter = answers.iterator()
        var conclusion = ""
        var details = ""
        while (answerIter.hasNext)
          val answer = answerIter.next()
          if (get[String](answer, "title") == "对课程的整体感受") {
            conclusion = get[String](answer, "value")
          } else {
            details = get[String](answer, "value")
          }
        if (!details.isBlank) {
          details = details.replaceAll("\n", """\\n""")
          details = details.replaceAll("'", "''")
          val feedbackSql = s"insert into qos.evaluate_data3(id,semester_no,clazz_no,teacher_no,course_no,teach_depart_no,std_no,created_at,conclusion,details)" +
            s" values($id,'$semesterNo','$crn','$teacherNo','$courseNo','$teachDepartNo','$studentNo','$createdAt','$conclusion','$details');"
          data3File.write(feedbackSql)
          data3File.write("\n")
        }
      }
    }
    data3File.close()
    println(new File(s"./data${semesterNo}.txt").getAbsolutePath)
  }

  def fetchFinal(semesterNo:String): Unit = {
    //create table qos.evaluate_data(semester_no varchar(20),clazz_no varchar(4),grade varchar(4),teacher_no varchar(15),category_rank integer,depart_rank integer,course_no varchar(20),score real,tickets integer,teach_depart_no varchar(10),teacher_depart_no varchar(10));
    //create table qos.evaluate_data2(semester_no varchar(20),clazz_no varchar(4),teacher_no varchar(15),score real,rank integer,indicator_name varchar(30),remark varchar(100));
    //create table qos.evaluate_data3(id bigint,semester_no varchar(20),clazz_no varchar(4),teacher_no varchar(15),course_no varchar(20),teach_depart_no varchar(20),std_no varchar(20),conclusion varchar(100),details varchar(1000),created_at varchar(30));
    val gson = new Gson()
    val utf8 = Charset.forName("UTF-8")

    val url = s"https://pjxt.ecupl.edu.cn/api/open/result/index?semester_no=${semesterNo}&limit=4000"
    val content = HttpUtils.getText(new URL(url), HttpMethods.GET, utf8, Some({ c =>
      c.setConnectTimeout(60 * 1000)
      c.setReadTimeout(60 * 1000)
    })).getText
    val a = gson.fromJson(content, classOf[ju.Map[String, Object]])
    val list = get[ju.List[ju.Map[String, Object]]](a, "data.list")
    val listIter = list.iterator()
    val data1File = new FileWriter(new File("./data1.sql"), utf8)
    val data2File = new FileWriter(new File("./data2.sql"), utf8)
    val data3File = new FileWriter(new File("./data3.sql"), utf8)

    var i=0
    while (listIter.hasNext) {
      i+=1
      val clazzData = listIter.next();
      val clazzNo = get[String](clazzData, "clazz_no")
      println(s"process ${i} ${clazzNo}")
      val crn =clazzNo.substring(9)
      val grade = get[String](clazzData, "grade")
      val semesterNo=get[String](clazzData, "semester_no")
      val teacherNo = get[String](clazzData, "teacher.job_no")
      val teachDepartNo=get[String](clazzData, "dept_no")
      val teacherDepartNo=get[String](clazzData, "teacher.dept_no")
      val subjectRank = get[Number](clazzData, "subject.rank").intValue()
      val departRank = get[Number](clazzData, "college.rank").intValue()
      val courseNo = get[String](clazzData, "course.course_no")
      val score = get[String](clazzData, "score")
      val stds = get[Number](clazzData, "students").intValue()
      val sql = s"insert into qos.evaluate_data(semester_no,clazz_no,grade,teacher_no,category_rank,depart_rank,course_no,score,tickets,teach_depart_no,teacher_depart_no)" +
        s" values('$semesterNo','$crn','$grade','$teacherNo',$subjectRank,$departRank,'$courseNo',$score,$stds,'$teachDepartNo','$teacherDepartNo');"
      data1File.write(sql)
      data1File.write("\n")
      val options = get[ju.List[ju.Map[String, Object]]](clazzData, "options")
      val optionIter = options.iterator()
      while (optionIter.hasNext) {
        val option = optionIter.next()
        val score = get[String](option, "score")
        val label = get[String](option, "label")
        val rank = get[Number](option, "rank").intValue()
        val comment = get[String](option, "comment")
        val sql2 = s"insert into qos.evaluate_data2(semester_no,clazz_no,teacher_no,score,rank,indicator_name,remark) values('$semesterNo','$crn','$teacherNo',$score,$rank,'$label','$comment');"
        data2File.write(sql2)
        data2File.write("\n")
      }
      val feedbackUrl = s"https://pjxt.ecupl.edu.cn/api/open/feedback/index?type=2&semester_no=${semesterNo}&limit=4000&clazz_no=${clazzNo}&teacher_no=${teacherNo}"
      val feedbackContent = HttpUtils.getText(new URL(feedbackUrl), HttpMethods.GET, utf8, Some({ c =>
        c.setConnectTimeout(60 * 1000);
        c.setReadTimeout(60 * 1000)
      })).getText
      val feedbackData = gson.fromJson(feedbackContent, classOf[ju.Map[String, Object]])
      val feedbacks = get[ju.List[ju.Map[String, Object]]](feedbackData, "data.list")
      val feedbackIter= feedbacks.iterator()
      while(feedbackIter.hasNext){
        val feedback= feedbackIter.next()
        val studentNo= get[String](feedback,"student_no")
        val createdAt= get[String](feedback,"created_at")
        val id= get[Number](feedback,"id").longValue()
        val answers = get[ju.List[ju.Map[String, Object]]](feedback, "answers")
        val answerIter= answers.iterator()
        var conclusion=""
        var details=""
        while(answerIter.hasNext)
          val answer = answerIter.next()
          if(get[String](answer,"title")=="对课程的整体感受"){
            conclusion = get[String](answer,"value")
          }else{
            details = get[String](answer,"value")
          }
        if(!details.isBlank) {
          details = details.replaceAll("\n","""\\n""")
          details = details.replaceAll("'","''")
          val feedbackSql=s"insert into qos.evaluate_data3(id,semester_no,clazz_no,teacher_no,course_no,teach_depart_no,std_no,created_at,conclusion,details)" +
            s" values($id,'$semesterNo','$crn','$teacherNo','$courseNo','$teachDepartNo','$studentNo','$createdAt','$conclusion','$details');"
          data3File.write(feedbackSql)
          data3File.write("\n")
        }
      }
    }
    data1File.close()
    data2File.close()
    data3File.close()
    println(new File("./data1.txt").getAbsolutePath)
  }

  def get[T](context: ju.Map[String, Object], property: String): T = {
    val parts = property.split("\\.")
    var data: Any = context
    parts foreach { part =>
      if (part.contains("[")) {
        val leftIdx = part.indexOf('[')
        val rightIdx = part.indexOf(']')
        val idx = part.substring(leftIdx + 1, rightIdx).toInt
        val a = data.asInstanceOf[ju.Map[String, Any]].get(part.substring(0, leftIdx))
        data = a.asInstanceOf[ju.List[Any]].get(idx)
      } else {
        data = data.asInstanceOf[ju.Map[String, Any]].get(part)
      }
    }
    data.asInstanceOf[T]
  }

}
