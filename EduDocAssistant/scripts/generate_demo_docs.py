"""生成演示用 DOCX（需在 ai-service 的 .venv 下运行，python-docx 已安装）。

产物：
  实验五_泛型类和泛型接口_模板.docx           教师上传解析模板规则
  实验五_泛型类和泛型接口_学生报告.docx       学生上传解析/提交的合规报告
  实验五_泛型类和泛型接口_按模板新建示例.docx 按模板新建后的骨架参考（可直接上传）
  实验五_泛型类和泛型接口_格式问题示例.docx   可选：用于格式校验演示（正文/标题/页边距不合规）
"""

from pathlib import Path

from docx import Document
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Mm, Pt, RGBColor

OUT = Path(__file__).resolve().parents[2] / "09_演示汇报" / "演示材料"
OUT.mkdir(parents=True, exist_ok=True)

# 模板/报告共用的版式口径
MARGINS = dict(top=25.4, bottom=25.4, left=31.75, right=25.4)
H1 = dict(ascii="Times New Roman", east="黑体", pt=16, line=1.5)
H2 = dict(ascii="Times New Roman", east="黑体", pt=14, line=1.5)
H3 = dict(ascii="Times New Roman", east="黑体", pt=12, line=1.5)
BODY = dict(ascii="Times New Roman", east="宋体", pt=10.5, line=1.5)
CODE = dict(ascii="Consolas", east="Consolas", pt=9, line=1.0)


def set_style(style, spec: dict, bold: bool = False):
    style.font.name = spec["ascii"]
    style.font.size = Pt(spec["pt"])
    style.font.bold = bold
    style.font.color.rgb = RGBColor(0, 0, 0)
    style.paragraph_format.line_spacing = spec["line"]
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.get_or_add_rFonts()
    rfonts.set(qn("w:ascii"), spec["ascii"])
    rfonts.set(qn("w:hAnsi"), spec["ascii"])
    rfonts.set(qn("w:eastAsia"), spec["east"])


def prepare(doc: Document, issue: bool = False, code_style: bool = True):
    sec = doc.sections[0]
    sec.top_margin = Mm(20 if issue else MARGINS["top"])
    sec.bottom_margin = Mm(20 if issue else MARGINS["bottom"])
    sec.left_margin = Mm(20 if issue else MARGINS["left"])
    sec.right_margin = Mm(20 if issue else MARGINS["right"])

    set_style(doc.styles["Heading 1"], H1, bold=True)
    set_style(doc.styles["Heading 2"], H2, bold=True)
    set_style(doc.styles["Heading 3"], H3, bold=True)
    set_style(doc.styles["Normal"], BODY)

    if issue:
        # 故意做错：正文/标题字体字号与模板不一致，页边距也不同
        set_style(doc.styles["Normal"], dict(ascii="Arial", east="仿宋", pt=12, line=1.0))
        for level in (1, 2, 3):
            set_style(doc.styles[f"Heading {level}"],
                      dict(ascii="Arial", east="宋体", pt=12, line=1.0), bold=False)

    title = doc.styles["Title"]
    set_style(title, dict(ascii="Times New Roman", east="黑体", pt=22, line=1.5), bold=True)
    title.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER

    if code_style:
        cs = doc.styles.add_style("代码", WD_STYLE_TYPE.PARAGRAPH)
        cs.base_style = doc.styles["Normal"]
        set_style(cs, CODE)
        cs.paragraph_format.space_after = Pt(0)

    if not issue:
        sec.header.paragraphs[0].text = "实验五 泛型类和泛型接口"
        sec.footer.paragraphs[0].text = "计算机科学与技术学院"


def h(doc: Document, text: str, level: int = 1):
    doc.add_paragraph(text, style=f"Heading {level}")


def p(doc: Document, text: str):
    doc.add_paragraph(text, style="Normal")


def bullets(doc: Document, items: list[str]):
    for item in items:
        doc.add_paragraph(item, style="List Bullet")


def code(doc: Document, text: str):
    for line in text.splitlines():
        doc.add_paragraph(line, style="代码")


def template_doc():
    doc = Document()
    prepare(doc, code_style=False)
    doc.add_paragraph("实验五 泛型类和泛型接口（课程模板）", style="Title")
    h(doc, "一、实验目的")
    p(doc, "（请在此填写实验目的：理解泛型、掌握泛型类与泛型接口、掌握通配符与多态。）")
    h(doc, "二、实验环境")
    p(doc, "（请在此填写开发语言、JDK 版本、IDE 与运行环境。）")
    h(doc, "三、实验内容")
    h(doc, "3.1 泛型类 Point<T>", 2)
    p(doc, "（请在此填写泛型类的设计思路、成员与方法说明。）")
    h(doc, "3.2 泛型接口", 2)
    p(doc, "（请在此填写泛型接口的定义、实现类与使用方式。）")
    h(doc, "3.2.1 泛型接口定义", 3)
    p(doc, "（请在此补充接口方法、类型参数约束与实现说明。）")
    h(doc, "四、实验结果")
    p(doc, "（请在此粘贴运行结果或测试结论。）")
    h(doc, "五、实验总结")
    p(doc, "（请在此总结实验收获、遇到的问题与解决方法。）")
    return doc


def report_doc(title="实验五 泛型类和泛型接口", skeleton=False, issue=False):
    doc = Document()
    prepare(doc, issue=issue)
    doc.add_paragraph(title, style="Title")

    h(doc, "一、实验目的")
    if skeleton:
        p(doc, "（请在此填写实验目的：理解泛型概念、掌握泛型类和泛型接口、掌握通配符与多态。）")
    else:
        p(doc, "本实验通过实现泛型类与泛型接口，理解类型参数在编译期提供的类型安全检查，"
               "并掌握泛型通配符在多态场景中的使用方式。")
        bullets(doc, [
            "理解泛型的概念、意义以及类型擦除带来的限制；",
            "掌握泛型类的定义、构造方法、成员方法和 getter/setter 的编写；",
            "掌握泛型接口的定义与实现，能够使用通配符处理不同类型的集合。",
        ])

    h(doc, "二、实验环境")
    p(doc, "开发语言：Java；JDK 版本：JDK 17；开发工具：IntelliJ IDEA 2023；"
           "运行环境：Windows 11，命令行编译运行。")

    h(doc, "三、实验内容")
    h(doc, "3.1 泛型类 Point<T>", 2)
    p(doc, "定义泛型类 Point<T extends Number>，使用两个类型为 T 的成员变量表示坐标 x、y，"
           "提供无参构造方法、带参构造方法、getter/setter，以及 translate(T dx, T dy) 平移方法。"
           "由于 T 的上界是 Number，不能直接使用加号，需要通过 doubleValue() 完成数值运算。")
    code(doc, """public class Point<T extends Number> {
    private T x;
    private T y;

    public Point() {}

    public Point(T x, T y) {
        this.x = x;
        this.y = y;
    }

    public T getX() { return x; }
    public void setX(T x) { this.x = x; }
    public T getY() { return y; }
    public void setY(T y) { this.y = y; }

    public void translate(T dx, T dy) {
        this.x = (T) Double.valueOf(x.doubleValue() + dx.doubleValue());
        this.y = (T) Double.valueOf(y.doubleValue() + dy.doubleValue());
    }
}""")

    h(doc, "3.1.1 泛型类的使用", 3)
    p(doc, "在测试类中分别创建 Point<Integer> 和 Point<Double> 对象，调用平移方法并输出坐标，"
           "验证同一套泛型代码可以处理不同的 Number 子类型。")

    h(doc, "3.2 泛型接口", 2)
    p(doc, "定义泛型接口 Shape<T extends Number>，声明 area() 方法；再定义 Rectangle<T extends Number> "
           "实现该接口，并在实现类中复用类型参数 T。")
    code(doc, """public interface Shape<T extends Number> {
    T area();
}

public class Rectangle<T extends Number> implements Shape<T> {
    private final T width;
    private final T height;

    public Rectangle(T width, T height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public T area() {
        return (T) Double.valueOf(width.doubleValue() * height.doubleValue());
    }
}""")

    h(doc, "3.3 测试类", 2)
    p(doc, "编写 TestPoint 类，创建泛型对象并调用平移方法；同时创建矩形对象验证泛型接口的实现。")
    code(doc, """public class TestPoint {
    public static void main(String[] args) {
        Point<Integer> p1 = new Point<>(1, 2);
        p1.translate(3, 4);
        System.out.println("p1 = (" + p1.getX() + ", " + p1.getY() + ")");

        Point<Double> p2 = new Point<>(1.5, 2.5);
        p2.translate(0.5, 1.0);
        System.out.println("p2 = (" + p2.getX() + ", " + p2.getY() + ")");

        Shape<Double> rectangle = new Rectangle<>(3.0, 4.0);
        System.out.println("area = " + rectangle.area());
    }
}""")

    h(doc, "四、运行结果")
    p(doc, "程序编译运行后输出如下结果，说明泛型类与泛型接口均能按预期工作。")
    table = doc.add_table(rows=1, cols=3)
    table.style = "Table Grid"
    for cell, text in zip(table.rows[0].cells, ("测试对象", "输入", "输出")):
        cell.text = text
    for values in (("Point<Integer>", "translate(3, 4)", "p1 = (4, 6)"),
                   ("Point<Double>", "translate(0.5, 1.0)", "p2 = (2.0, 3.5)"),
                   ("Rectangle<Double>", "area()", "area = 12.0")):
        cells = table.add_row().cells
        for cell, text in zip(cells, values):
            cell.text = text

    h(doc, "五、实验总结")
    if skeleton:
        p(doc, "（请在此总结实验收获、遇到的问题与解决方法。）")
    else:
        p(doc, "通过本次实验，掌握了泛型类与泛型接口的定义方式，理解了类型参数上界、"
               "通配符以及类型擦除对运行时的影响。实验中需要注意 Number 子类型不能直接参与算术运算，"
               "应先转换为 double 再处理，并在需要时进行类型转换。")
    return doc


def main():
    template_doc().save(OUT / "实验五_泛型类和泛型接口_模板.docx")
    report_doc().save(OUT / "实验五_泛型类和泛型接口_学生报告.docx")
    report_doc("实验五 泛型类和泛型接口（按模板新建示例）", skeleton=True).save(
        OUT / "实验五_泛型类和泛型接口_按模板新建示例.docx")
    report_doc("实验五 泛型类和泛型接口（格式问题示例）", skeleton=True, issue=True).save(
        OUT / "实验五_泛型类和泛型接口_格式问题示例.docx")
    print("generated:", *(p.name for p in sorted(OUT.glob("*.docx"))), sep="\n  ")


if __name__ == "__main__":
    main()