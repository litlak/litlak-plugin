package io.github.litlak.plugin.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.Interface;
import org.mybatis.generator.api.dom.java.JavaVisibility;
import org.mybatis.generator.api.dom.java.Method;
import org.mybatis.generator.api.dom.java.Parameter;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;

/**
 * 新增的自定义的方法插件 新增方法: <br>
 * listXXX(从SelectByExample变) <br>
 * batchInsert <br>
 * insertOrUpdate <br>
 * 
 * @author Scomy
 *
 * @date 2019-03-28
 */
public class CustomMethodGenerator extends PluginAdapter {
	
	@Override
	public boolean validate(List<String> warnings) {
		return true;
	}

	@Override
	public boolean clientGenerated(Interface interfaze, TopLevelClass topLevelClass,
			IntrospectedTable introspectedTable) {
		final FullyQualifiedJavaType domainJavaType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
		String domainObjectName = domainJavaType.getShortName();
		
		// batch insert
		{
			Method method = new Method();
			method.setName("batchInsert");
			method.setReturnType(new FullyQualifiedJavaType("int"));
			method.addParameter(new Parameter(
					new FullyQualifiedJavaType("java.util.List<" + domainObjectName + ">"), "list"));
			interfaze.addMethod(method);
			interfaze.addImportedType(new FullyQualifiedJavaType("java.util.List"));
		}
		
		// insertOrUpdate
		{
			Method method = new Method();
			method.setName("insertOrUpdate");
			method.addParameter(new Parameter(
					new FullyQualifiedJavaType(domainObjectName), "record"));
			method.setReturnType(new FullyQualifiedJavaType("int"));
			interfaze.addMethod(method);
		}
		// listXXX
		{
			Method method = new Method();
			method.setName("list"+domainObjectName);
			method.addParameter(new Parameter(
					new FullyQualifiedJavaType(domainObjectName), "req"));
			method.setReturnType(new FullyQualifiedJavaType("java.util.List<" + domainObjectName + ">"));
			interfaze.addMethod(method);
			interfaze.addImportedType(new FullyQualifiedJavaType("java.util.List"));
		}

		// list by primary key
		List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
		if (primaryKeyColumns != null && primaryKeyColumns.size() == 1) {
			Method listMethod = new Method();
			listMethod.setName("listByPrimaryKey");
			listMethod.setReturnType(new FullyQualifiedJavaType("java.util.List<" + domainObjectName + ">"));

			FullyQualifiedJavaType primaryKeyType = primaryKeyColumns.get(0).getFullyQualifiedJavaType();
			Parameter parameter = new Parameter(
					new FullyQualifiedJavaType("java.util.List<" + primaryKeyType.getShortName() + ">"), "list");
			listMethod.addParameter(parameter);
			interfaze.addMethod(listMethod);
			interfaze.addImportedType(new FullyQualifiedJavaType("java.util.List"));
		}

		return super.clientGenerated(interfaze, topLevelClass, introspectedTable);
	}

	private static String batchValues(IntrospectedColumn column) {
		return new StringBuilder().append("#{item.").append(column.getJavaProperty()).append(",jdbcType=")
				.append(column.getJdbcTypeName()).append('}').toString();
	}
	
	private static String values(IntrospectedColumn column) {
		return new StringBuilder().append("#{").append(column.getJavaProperty()).append(",jdbcType=")
				.append(column.getJdbcTypeName()).append('}').toString();
	}
	
	@Override
	public boolean sqlMapDocumentGenerated(Document document, IntrospectedTable introspectedTable) {
		final FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
		final String tableName = table.getIntrospectedTableName();
		final String domainObjectFullyName = introspectedTable.getBaseRecordType();
		final List<IntrospectedColumn> baseColumns = introspectedTable.getBaseColumns();
		final List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
		
		XmlElement root = document.getRootElement();
		// batch insert
		{
			// batch insert
			XmlElement batchInsert = new XmlElement("insert");
			batchInsert.addAttribute(new Attribute("id", "batchInsert"));
			batchInsert.addAttribute(new Attribute("parameterType", domainObjectFullyName));
			batchInsert.addElement(new TextElement("insert into " + tableName + " ("));
			for (int i = 0; i < baseColumns.size(); i++) {
				if (i % 2 != 0) {
					continue;
				}
				int maxSize = Math.min(2, baseColumns.size() - i);
				int restNum = baseColumns.size() - i;
				batchInsert.addElement(new TextElement(
						"  " + baseColumns.stream().skip(i).limit(maxSize).map(e -> e.getActualColumnName())
								.collect(Collectors.joining(",")) + (restNum > 2 ? "," : "")));
			}
			batchInsert.addElement(new TextElement(") values "));
			XmlElement foreach = new XmlElement("foreach");
			foreach.addAttribute(new Attribute("collection", "list"));
			foreach.addAttribute(new Attribute("item", "item"));
			foreach.addAttribute(new Attribute("separator", ","));
			foreach.addElement(new TextElement("("));
			for (int i = 0; i < baseColumns.size(); i++) {
				if (i % 2 != 0) {
					continue;
				}
				int maxSize = Math.min(2, baseColumns.size() - i);
				int restNum = baseColumns.size() - i;
				foreach.addElement(new TextElement("  " + baseColumns.stream().skip(i).limit(maxSize)
						.map(e -> batchValues(e)).collect(Collectors.joining(",")) + (restNum > 2 ? "," : "")));
			}
			foreach.addElement(new TextElement(")"));
			batchInsert.addElement(foreach);
			root.addElement(batchInsert);
		}
		
		// insertOrUpdate
		if(primaryKeyColumns != null && primaryKeyColumns.size()>0){
			baseColumns.stream().map(e->e.getActualColumnName()).collect(Collectors.joining(","));
			XmlElement insertOrUpdate = new XmlElement("insert");
			insertOrUpdate.addAttribute(new Attribute("id", "insertOrUpdate"));
			insertOrUpdate.addAttribute(new Attribute("parameterType", domainObjectFullyName));
			insertOrUpdate.addElement(new TextElement("insert into " + tableName + " ("));
			for (int i = 0; i < baseColumns.size(); i++) {
				if (i % 2 != 0) {
					continue;
				}
				int maxSize = Math.min(2, baseColumns.size() - i);
				int restNum = baseColumns.size() - i;
				insertOrUpdate.addElement(new TextElement(
						"  " + baseColumns.stream().skip(i).limit(maxSize).map(e -> e.getActualColumnName())
								.collect(Collectors.joining(",")) + (restNum > 2 ? "," : "")));
			}
			insertOrUpdate.addElement(new TextElement(") values ("));
			for (int i = 0; i < baseColumns.size(); i++) {
				if (i % 2 != 0) {
					continue;
				}
				int maxSize = Math.min(2, baseColumns.size() - i);
				int restNum = baseColumns.size() - i;
				insertOrUpdate.addElement(new TextElement("  " + baseColumns.stream().skip(i).limit(maxSize)
						.map(e -> values(e)).collect(Collectors.joining(",")) + (restNum > 2 ? "," : "")));
			}
			insertOrUpdate.addElement(new TextElement(") on duplicate key update "));
			for (IntrospectedColumn column : baseColumns) {
				if (primaryKeyColumns.contains(column)) {
					continue;
				}
				insertOrUpdate.addElement(new TextElement("  "+column.getActualColumnName() + " = " + values(column) + ","));
			}
			insertOrUpdate.addElement(new TextElement("  gmt_modified = now() "));
			
			root.addElement(insertOrUpdate);
		}

		// listXXX
		{
			XmlElement selectAll = new XmlElement("select");
			selectAll.addAttribute(new Attribute("id", "list" + table.getDomainObjectName()));
			selectAll.addAttribute(new Attribute("resultMap", "BaseResultMap"));
			selectAll.addAttribute(new Attribute("parameterType", domainObjectFullyName));

			TextElement selectSql = new TextElement("select");
			selectAll.addElement(selectSql);
			XmlElement include = new XmlElement("include");
			include.addAttribute(new Attribute("refid", "Base_Column_List"));
			selectAll.addElement(include);
			selectAll.addElement(new TextElement("from " + tableName));

			root.addElement(selectAll);
		}

		// list by primary key
		if (primaryKeyColumns != null && primaryKeyColumns.size() == 1) {
			XmlElement listByPrimaryKey = new XmlElement("select");
			listByPrimaryKey.addAttribute(new Attribute("id", "listByPrimaryKey"));
			listByPrimaryKey.addAttribute(new Attribute("resultMap", "BaseResultMap"));

			TextElement selectEle = new TextElement("select");
			listByPrimaryKey.addElement(selectEle);
			XmlElement includeEle = new XmlElement("include");
			includeEle.addAttribute(new Attribute("refid", "Base_Column_List"));
			listByPrimaryKey.addElement(includeEle);
			listByPrimaryKey.addElement(new TextElement("from " + tableName));
			listByPrimaryKey
					.addElement(new TextElement("where " + primaryKeyColumns.get(0).getActualColumnName() + " in ("));
			XmlElement foreach = new XmlElement("foreach");
			foreach.addAttribute(new Attribute("collection", "list"));
			foreach.addAttribute(new Attribute("item", "item"));
			foreach.addAttribute(new Attribute("separator", ","));
			foreach.addElement(new TextElement("#{item}"));
			listByPrimaryKey.addElement(foreach);
			listByPrimaryKey.addElement(new TextElement(")"));
			root.addElement(listByPrimaryKey);
		}

		return super.sqlMapDocumentGenerated(document, introspectedTable); 
	}

    @Override
    public List<GeneratedJavaFile> contextGenerateAdditionalJavaFiles(
            IntrospectedTable introspectedTable) {
    	List<GeneratedJavaFile> javaFiles = new ArrayList<>();
    	final String remarks = introspectedTable.getRemarks();
    	final List<IntrospectedColumn> primaryKeyColumns = introspectedTable.getPrimaryKeyColumns();
    	FullyQualifiedJavaType primaryKeyType = null;
		if (primaryKeyColumns != null && primaryKeyColumns.size() > 0) {
			primaryKeyType = primaryKeyColumns.get(0).getFullyQualifiedJavaType();
		}
		final FullyQualifiedJavaType domainJavaType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
		final String domainObjectName = domainJavaType.getShortName();
		final FullyQualifiedJavaType mapperJavaType = new FullyQualifiedJavaType(introspectedTable.getMyBatis3JavaMapperType());
		final String mapperTypeName = mapperJavaType.getShortNameWithoutTypeArguments();
		final String mapperFieldName = String.valueOf(mapperTypeName.charAt(0)).toLowerCase() + mapperTypeName.substring(1);
    	
		//----------------------- Service ------------------------
		String serviceFullQualifiedName = properties.getProperty("servicePackage") + "." + domainObjectName + "Service";
		TopLevelClass serviceClass = new TopLevelClass(serviceFullQualifiedName);
		serviceClass.setVisibility(JavaVisibility.PUBLIC);
		serviceClass.addImportedType(new FullyQualifiedJavaType("org.springframework.stereotype.Service"));
		serviceClass.addImportedType(new FullyQualifiedJavaType("org.springframework.beans.factory.annotation.Autowired"));
		serviceClass.addAnnotation("@Service");
		serviceClass.addImportedType(domainJavaType);
		serviceClass.addImportedType(mapperJavaType);
		serviceClass.addImportedType(new FullyQualifiedJavaType("java.util.List"));
		Field mapperField = new Field(mapperFieldName, mapperJavaType);
		mapperField.addAnnotation("@Autowired");
		mapperField.setVisibility(JavaVisibility.PRIVATE);
		serviceClass.addField(mapperField);
		
		// service.add 
		Method addMethod = new Method();
		addMethod.setVisibility(JavaVisibility.PUBLIC);
		addMethod.addParameter(new Parameter(domainJavaType, "req"));
		addMethod.setName("add" + domainObjectName);
		addMethod.addBodyLine(mapperFieldName + ".insert(req);");
		serviceClass.addMethod(addMethod);
		
		// service.delete
		Method deleteMethod = null;
		if (primaryKeyType != null) {
			deleteMethod = new Method();
			deleteMethod.setVisibility(JavaVisibility.PUBLIC);
			deleteMethod.addParameter(new Parameter(primaryKeyType, "id"));
			deleteMethod.setName("delete" + domainObjectName);
			deleteMethod.addBodyLine(mapperFieldName + ".deleteByPrimaryKey(id);");
			serviceClass.addMethod(deleteMethod);
		}
		
		// service.update 
		Method updateMethod = new Method();
		updateMethod.setVisibility(JavaVisibility.PUBLIC);
		updateMethod.addParameter(new Parameter(domainJavaType, "req"));
		updateMethod.setName("update" + domainObjectName);
		updateMethod.addBodyLine(mapperFieldName + ".updateByPrimaryKey(req);");
		serviceClass.addMethod(updateMethod);
		
		// service.get
		Method getMethod = null;
		if (primaryKeyType != null) {
			getMethod = new Method();
			getMethod.setVisibility(JavaVisibility.PUBLIC);
			getMethod.addParameter(new Parameter(primaryKeyType, "req"));
			getMethod.setName("get" + domainObjectName);
			getMethod.setReturnType(domainJavaType);
			getMethod.addBodyLine("return "+mapperFieldName + ".selectByPrimaryKey(req);");
			serviceClass.addMethod(getMethod);
		}
		
		// service.listByIds
		FullyQualifiedJavaType idsJavaType = new FullyQualifiedJavaType("java.util.List<" + primaryKeyType.getShortName() + ">");
		Method listByIdsMethod = null;
		if (primaryKeyType != null) {
			FullyQualifiedJavaType domainListJavaType = new FullyQualifiedJavaType("java.util.List<" + domainObjectName + ">");
			listByIdsMethod = new Method();
			listByIdsMethod.setVisibility(JavaVisibility.PUBLIC);
			listByIdsMethod.setName("listByIds");
			listByIdsMethod.addParameter(new Parameter(idsJavaType, "ids"));
			listByIdsMethod.setReturnType(domainListJavaType);
			listByIdsMethod.addBodyLine("return " + mapperFieldName + ".listByPrimaryKey(ids);");
			serviceClass.addMethod(listByIdsMethod);
		}
		
		// service.listXXX
		Method listDomain = new Method();
		listDomain.setVisibility(JavaVisibility.PUBLIC);
		listDomain.setName("list" + domainObjectName);
		listDomain.addParameter(new Parameter(domainJavaType, "req"));
		listDomain.setReturnType(new FullyQualifiedJavaType("java.util.List<" + domainObjectName + ">"));
		listDomain.addBodyLine("return " + mapperFieldName + ".list" + domainObjectName + "(req);");
		serviceClass.addMethod(listDomain);
		GeneratedJavaFile service = new GeneratedJavaFile(serviceClass, properties.getProperty("javaTargetProject"),
				context.getJavaFormatter());
		javaFiles.add(service);
		
		//----------------------- Controller ------------------------
		String responseTypeName = properties.getProperty("responseGenericityType");
		final FullyQualifiedJavaType serviceJavaType = new FullyQualifiedJavaType(serviceFullQualifiedName);
		final FullyQualifiedJavaType responseType = new FullyQualifiedJavaType(responseTypeName);
		final FullyQualifiedJavaType longResponseType = new FullyQualifiedJavaType(responseTypeName+"<Long>");
		final FullyQualifiedJavaType domainResponseType = new FullyQualifiedJavaType(responseTypeName+"<"+domainObjectName+">");
		final FullyQualifiedJavaType listResponseType = new FullyQualifiedJavaType(responseTypeName+"<List<"+domainObjectName+">>");
		final String responseObjectName = responseType.getShortNameWithoutTypeArguments();
		TopLevelClass controllerClass = new TopLevelClass(properties.getProperty("controllerPackage") + "." + domainObjectName + "Controller");
		controllerClass.setVisibility(JavaVisibility.PUBLIC);
		controllerClass.addImportedType(new FullyQualifiedJavaType("org.springframework.beans.factory.annotation.Autowired"));
		controllerClass.addImportedType(new FullyQualifiedJavaType("org.springframework.web.bind.annotation.RestController"));
		controllerClass.addImportedType(new FullyQualifiedJavaType("org.springframework.web.bind.annotation.GetMapping"));
		controllerClass.addImportedType(new FullyQualifiedJavaType("org.springframework.web.bind.annotation.PostMapping"));
		controllerClass.addImportedType(new FullyQualifiedJavaType("org.springframework.web.bind.annotation.RequestParam"));
		controllerClass.addImportedType(new FullyQualifiedJavaType("org.springframework.web.bind.annotation.RequestBody"));
		controllerClass.addImportedType(new FullyQualifiedJavaType("io.swagger.annotations.Api"));
		controllerClass.addImportedType(new FullyQualifiedJavaType("io.swagger.annotations.ApiOperation"));
		controllerClass.addImportedType(domainJavaType);
		controllerClass.addImportedType(serviceJavaType);
		controllerClass.addImportedType(responseType);
		controllerClass.addImportedType(new FullyQualifiedJavaType("java.util.List"));
		controllerClass.addAnnotation("@RestController");
		controllerClass.addAnnotation("@Api(tags = \""+remarks+"\")");
		
		String seriveFieldName = String.valueOf(domainObjectName.charAt(0)).toLowerCase() + domainObjectName.substring(1)+"Service";
		Field seriveField = new Field(seriveFieldName, serviceJavaType);
		seriveField.setVisibility(JavaVisibility.PRIVATE);
		seriveField.addAnnotation("@Autowired");
		controllerClass.addField(seriveField);
		
		// controller.add
		Method addApi = new Method();
		addApi.setVisibility(JavaVisibility.PUBLIC);
		addApi.addAnnotation("@PostMapping(value = \"/" + domainObjectName + "/add\")");
		addApi.addAnnotation("@ApiOperation(value = \"新增" + remarks + "\")");
		final Parameter domainParamter = new Parameter(domainJavaType, "req");
		domainParamter.addAnnotation("@RequestBody");
		addApi.addParameter(domainParamter);
		addApi.setName(addMethod.getName());
		addApi.setReturnType(longResponseType);
		addApi.addBodyLine(seriveFieldName + "." + addMethod.getName() + "(req);");
		addApi.addBodyLine("return new " + responseObjectName + "<>();");
		controllerClass.addMethod(addApi);

		// controller.delete
		if (deleteMethod != null) {
			final Parameter idParamter = new Parameter(deleteMethod.getParameters().get(0).getType(), "id");
			idParamter.addAnnotation("@RequestParam(\"id\")");
			Method deleteApi = new Method();
			deleteApi.setVisibility(JavaVisibility.PUBLIC);
			deleteApi.addAnnotation("@PostMapping(value = \"/" + domainObjectName + "/delete\")");
			deleteApi.addAnnotation("@ApiOperation(value = \"删除" + remarks + "\")");
			deleteApi.addParameter(idParamter);
			deleteApi.setName(deleteMethod.getName());
			deleteApi.setReturnType(longResponseType);
			deleteApi.addBodyLine(seriveFieldName + "." + deleteMethod.getName() + "(id);");
			deleteApi.addBodyLine("return new " + responseObjectName + "<>();");
			controllerClass.addMethod(deleteApi);
		}

		// controller.update
		Method updateApi = new Method();
		updateApi.setVisibility(JavaVisibility.PUBLIC);
		updateApi.addAnnotation("@PostMapping(value = \"/" + domainObjectName + "/update\")");
		updateApi.addAnnotation("@ApiOperation(value = \"更新" + remarks + "\")");
		updateApi.setName(updateMethod.getName());
		updateApi.addParameter(domainParamter);
		updateApi.setReturnType(longResponseType);
		updateApi.addBodyLine(seriveFieldName + "." + updateMethod.getName() + "(req);");
		updateApi.addBodyLine("return new " + responseObjectName + "<>();");
		controllerClass.addMethod(updateApi);

		// service.get
		if (getMethod != null) {
			final Parameter idParamter = new Parameter(deleteMethod.getParameters().get(0).getType(), "id");
			idParamter.addAnnotation("@RequestParam(\"id\")");
			Method getApi = new Method();
			getApi.setVisibility(JavaVisibility.PUBLIC);
			getApi.addAnnotation("@GetMapping(value = \"/" + domainObjectName + "/get\")");
			getApi.addAnnotation("@ApiOperation(value = \"详情查询" + remarks + "\")");
			getApi.addParameter(idParamter);
			getApi.setName(getMethod.getName());
			getApi.setReturnType(domainResponseType);
			getApi.addBodyLine("return new " + responseObjectName + "<>("+seriveFieldName + "." + getApi.getName() + "(id)"+");");
			controllerClass.addMethod(getApi);
		}

		// controller.listByIds
		if (listByIdsMethod != null) {
			Method listByIdsApi = new Method();
			listByIdsApi.setVisibility(JavaVisibility.PUBLIC);
			Parameter idsParameter = new Parameter(idsJavaType, "ids");
			idsParameter.addAnnotation("@RequestBody");
			listByIdsApi.addAnnotation("@PostMapping(value = \"/" + domainObjectName + "/listByIds\")");
			listByIdsApi.addAnnotation("@ApiOperation(value = \"列表查询" + remarks + "-指定ID\")");
			listByIdsApi.addParameter(idsParameter);
			listByIdsApi.setName(listByIdsMethod.getName());
			listByIdsApi.setReturnType(listResponseType);
			listByIdsApi.addBodyLine("return new " + responseObjectName + "<>("+seriveFieldName + "." + listByIdsApi.getName() + "(ids)"+");");
			controllerClass.addMethod(listByIdsApi);
		}

		// controller.listXXX
		Method listApi = new Method();
		listApi.setVisibility(JavaVisibility.PUBLIC);
		listApi.addAnnotation("@PostMapping(value = \"/" + domainObjectName + "/list\")");
		listApi.addAnnotation("@ApiOperation(value = \"列表查询" + remarks + "-复合参数\")");
		listApi.addParameter(domainParamter);
		listApi.setName(listDomain.getName());
		listApi.setReturnType(listResponseType);
		listApi.addBodyLine("return new " + responseObjectName + "<>("+seriveFieldName + "." + listDomain.getName() + "(req)"+");");
		controllerClass.addMethod(listApi);
		
		GeneratedJavaFile controller = new GeneratedJavaFile(controllerClass,
				properties.getProperty("javaTargetProject"), context.getJavaFormatter());
		javaFiles.add(controller);
		
		return javaFiles;
    }
}
