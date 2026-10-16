package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLDataType;
import com.alibaba.druid.sql.ast.SQLDataTypeImpl;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.db2.parser.DB2ExprParser;
import com.alibaba.druid.sql.dialect.db2.parser.DB2StatementParser;
import com.alibaba.druid.sql.parser.Lexer;
import com.alibaba.druid.sql.parser.Token;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Preserve DB2 string units unsupported by Druid 1.2.20, without rewriting SQL text. */
final class PortableSqlParser {
    private PortableSqlParser() { }

    static List<SQLStatement> parse(String sql, DatabaseType type) {
        if (type != DatabaseType.DB2) return SQLUtils.parseStatements(sql, DdlDialect.parser(type));
        return new Db2Statements(sql).parseStatementList();
    }

    private static final class Db2Statements extends DB2StatementParser {
        Db2Statements(String sql) {
            super(sql);
            exprParser = new Db2Expressions(getLexer());
        }
    }

    private static final class Db2Expressions extends DB2ExprParser {
        Db2Expressions(Lexer lexer) { super(lexer); }

        @Override public SQLDataType parseDataType(boolean restrict) {
            var saved = lexer.mark();
            String name = lexer.stringVal();
            if (name != null && Set.of("CHAR", "CHARACTER", "VARCHAR").contains(name.toUpperCase(Locale.ROOT))) {
                name = name.toUpperCase(Locale.ROOT);
                lexer.nextToken();
                if (name.equals("CHARACTER") && lexer.identifierEquals("VARYING")) {
                    name = "VARCHAR";
                    lexer.nextToken();
                }
                if (lexer.token() == Token.LPAREN) {
                    lexer.nextToken();
                    if (lexer.token() == Token.LITERAL_INT) {
                        Number size = lexer.integerValue();
                        lexer.nextToken();
                        String unit = lexer.stringVal();
                        if (unit != null && Set.of("OCTETS", "CODEUNITS16", "CODEUNITS32").contains(unit.toUpperCase(Locale.ROOT))) {
                            lexer.nextToken();
                            if (lexer.token() == Token.RPAREN) {
                                lexer.nextToken();
                                return new Db2StringType(name, size, unit.toUpperCase(Locale.ROOT));
                            }
                        }
                    }
                }
            }
            lexer.reset(saved);
            return super.parseDataType(restrict);
        }
    }

    private static final class Db2StringType extends SQLDataTypeImpl {
        private final String unit;
        Db2StringType(String name, Number size, String unit) {
            super(name, new com.alibaba.druid.sql.ast.expr.SQLIntegerExpr(size));
            this.unit = unit;
        }
        @Override protected void accept0(SQLASTVisitor visitor) {
            if (visitor instanceof SQLASTOutputVisitor output) {
                output.print(getName());
                output.print('(');
                getArguments().getFirst().accept(visitor);
                output.print(' ');
                output.print(unit);
                output.print(')');
            } else super.accept0(visitor);
        }
        @Override public Db2StringType clone() {
            var copy = new Db2StringType(getName(), 0, unit);
            copy.getArguments().clear();
            cloneTo(copy);
            return copy;
        }
    }
}
