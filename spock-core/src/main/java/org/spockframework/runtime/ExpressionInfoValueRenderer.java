package org.spockframework.runtime;

import groovy.lang.GString;
import org.spockframework.runtime.condition.EditDistance;
import org.spockframework.runtime.condition.EditPathRenderer;
import org.spockframework.runtime.model.ExpressionInfo;
import org.spockframework.util.GroovyRuntimeUtil;
import org.spockframework.util.Objects;
import org.spockframework.util.Nullable;

public class ExpressionInfoValueRenderer {
  private final ExpressionInfo expr;

  private ExpressionInfoValueRenderer(ExpressionInfo expr) {
    this.expr = expr;
  }

  public static void render(ExpressionInfo expr) {
    new ExpressionInfoValueRenderer(expr).render();
  }

  private void render() {
    for (ExpressionInfo expr : this.expr.inPostfixOrder(true)) {
      expr.setRenderedValue(renderValue(expr));
    }
  }

  /**
   * Returns a string representation of a value, or <tt>null</tt> if
   * the value should not be shown (because it does not add any valuable
   * information). Note that the method may also change rendered values
   * of child expression.
   *
   * @param expr the expression whose value is to be rendered
   * @return a string representation of the value
   */
  @Nullable
  private String renderValue(ExpressionInfo expr) {
    Object value = expr.getValue();

    if (value == null) return "null";
    if ("".equals(value)) return "\"\""; // value.equals() might throw exception, so we use "".equals() instead

    String str;

    try {
      str = doRenderValue(expr);
    } catch (Exception e) {
      return String.format("%s (renderer threw %s)",
          javaLangObjectToString(value), e.getClass().getSimpleName());
    }

    if (str == null || str.equals("")) {
      return javaLangObjectToString(value);
    }

    // only print enum values that add valuable information
    if (value instanceof Enum) {
      String text = expr.getText().trim();
      int index = text.lastIndexOf('.');
      String potentialEnumConstantNameInText = text.substring(index + 1);
      if (str.equals(potentialEnumConstantNameInText)) return null;
    }

    return str;
  }

  private String javaLangObjectToString(Object value) {
    String hash = Integer.toHexString(System.identityHashCode(value));
    return value.getClass().getName() + "@" + hash;
  }

  private String doRenderValue(ExpressionInfo expr) {
    String result = renderAsStringComparison(expr);
    if (result != null) return result;
    
    result = renderAsTypeHintedComparison(expr);
    if (result != null) return result;
    
    return GroovyRuntimeUtil.toString(expr.getValue());
  }
  
  private String renderAsStringComparison(ExpressionInfo expr) {
    if (expr.isEqualityComparison(String.class, GString.class)) {
      // here values can't be null
      String str1 = expr.getChildren().get(0).getValue().toString();
      String str2 = expr.getChildren().get(1).getValue().toString();
      EditDistance dist = new EditDistance(str1, str2);
      return String.format("false\n%d difference%s (%d%% similarity)\n%s",
          dist.getDistance(), dist.getDistance() == 1 ? "" : "s", dist.getSimilarityInPercent(),
          new EditPathRenderer().render(str1, str2, dist.calculatePath()));
    }

    return null;
  }
  
  private String renderAsTypeHintedComparison(ExpressionInfo expr) {
    if (expr.isEqualityComparison()) {
      ExpressionInfo expr1 = expr.getChildren().get(0);
      ExpressionInfo expr2 = expr.getChildren().get(1);
      if (Objects.eitherNull(expr1.getRenderedValue(), expr2.getRenderedValue())) {
        // at least one of the values should not be rendered; hence don't add type hint
        return null;
      }
      Class<?> expr1Type = Objects.voidAwareGetClass(expr1.getValue());
      Class<?> expr2Type = Objects.voidAwareGetClass(expr2.getValue());
      if (expr1Type != expr2Type
          && expr1.getRenderedValue().equals(expr2.getRenderedValue())) {
        expr1.setRenderedValue(expr1.getRenderedValue() + " (" + expr1Type.getName() + ")");
        expr2.setRenderedValue(expr2.getRenderedValue() + " (" + expr2Type.getName() + ")");
        return "false";
      }
    }

    return null;
  }
}