package io.github.litlak.plugin.generator;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.TopLevelClass;

/**
 * 
 * @author Scomy
 *
 * @date 2019-03-28
 */
public class Swagger2Generator extends PluginAdapter {

	@Override
	public boolean validate(List<String> warnings) {
		return true;
	}

	@Override
	public boolean modelFieldGenerated(Field field, TopLevelClass topLevelClass, IntrospectedColumn introspectedColumn,
			IntrospectedTable introspectedTable, ModelClassType modelClassType) {

		if (!contains(topLevelClass.getAnnotations(), "@ApiModel")) {
			boolean shortModelValue = Boolean.getBoolean(properties.getProperty("shortModelValue"));
			FullyQualifiedJavaType type = topLevelClass.getType();
			String modelValue = shortModelValue ? type.getShortName() : type.toString();
			String remarks = introspectedTable.getRemarks();
			String classAnnotation = "@ApiModel(value = \"" + modelValue + "\", description = \"" + remarks + "\")";
			topLevelClass.addAnnotation(classAnnotation);
		}

		Set<FullyQualifiedJavaType> importedTypes = topLevelClass.getImportedTypes();
		FullyQualifiedJavaType modelJavaType = new FullyQualifiedJavaType("io.swagger.annotations.ApiModel");
		if (!importedTypes.contains(modelJavaType)) {
			topLevelClass.addImportedType(modelJavaType);
		}
		FullyQualifiedJavaType propertyJavaType = new FullyQualifiedJavaType("io.swagger.annotations.ApiModelProperty");
		if (!importedTypes.contains(propertyJavaType)) {
			topLevelClass.addImportedType(propertyJavaType);
		}

		List<String> fieldAnnotations = field.getAnnotations();
		if (!contains(fieldAnnotations, "@ApiModelProperty")) {
			field.addAnnotation(generatorAnnotation(introspectedColumn));
		}
		return super.modelFieldGenerated(field, topLevelClass, introspectedColumn, introspectedTable, modelClassType);
	}

	private String generatorAnnotation(IntrospectedColumn introspectedColumn) {
		StringBuilder sb = new StringBuilder("@ApiModelProperty(");

		sb.append("value = \"" + introspectedColumn.getRemarks() + "\"");// 名字
		if (introspectedColumn.getJdbcTypeName() == JDBCType.TIMESTAMP.getName()) {
			sb.append(", ");
			sb.append("example = \""
					+ new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\"");// 默认值
		} else {
			if (introspectedColumn.getDefaultValue() != null) {
				sb.append(", ");
				sb.append("example = \"" + introspectedColumn.getDefaultValue() + "\"");// 默认值
			}
		}

		sb.append(')');
		return sb.toString();
	}

	private boolean contains(Collection<String> list, String string0) {
		for (String string : list) {
			if (string.contains(string0)) {
				return true;
			}
		}
		return false;
	}
}
