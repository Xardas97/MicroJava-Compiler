package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{
    // ukljucivanje informacije o poziciji tokena
    private Symbol new_symbol(int type) {
        return new Symbol(type, yyline+1, yycolumn);
    }
    
    // ukljucivanje informacije o poziciji tokena
    private Symbol new_symbol(int type, Object value) {
        return new Symbol(type, yyline+1, yycolumn, value);
    }
%}

%cup
%line
%column

%xstate COMMENT

%eofval{
    return new_symbol(sym.EOF);
%eofval}

%%

" "     { }
"\b"    { }
"\t"    { }
"\r\n"  { }
"\f"    { }

"program"   { return new_symbol(sym.PROG,      yytext()); }
"const"     { return new_symbol(sym.CONST,     yytext()); }
"read"      { return new_symbol(sym.READ,      yytext()); }
"print"     { return new_symbol(sym.PRINT,     yytext()); }
"return"    { return new_symbol(sym.RETURN,    yytext()); }
"void"      { return new_symbol(sym.VOID,      yytext()); }
"new"       { return new_symbol(sym.NEW,       yytext()); }
"do"        { return new_symbol(sym.DO,        yytext()); }
"while"     { return new_symbol(sym.WHILE,     yytext()); }
"break"     { return new_symbol(sym.BREAK,     yytext()); }
"continue"  { return new_symbol(sym.CONTINUE,  yytext()); }
"switch"    { return new_symbol(sym.SWITCH,    yytext()); }
"case"      { return new_symbol(sym.CASE,      yytext()); }
"yield"     { return new_symbol(sym.YIELD,     yytext()); }
"default"   { return new_symbol(sym.DEFAULT,   yytext()); }
"="         { return new_symbol(sym.ASSIGN,    yytext()); }
"+"         { return new_symbol(sym.PLUS,      yytext()); }
"-"         { return new_symbol(sym.MINUS,     yytext()); }
"*"         { return new_symbol(sym.MULTIPLE,  yytext()); }
"/"         { return new_symbol(sym.DIVIDE,    yytext()); }
"%"         { return new_symbol(sym.MODULO,    yytext()); }
"++"        { return new_symbol(sym.INCREMENT, yytext()); }
"--"        { return new_symbol(sym.DECREMENT, yytext()); }
"&&"        { return new_symbol(sym.AND,       yytext()); }
"||"        { return new_symbol(sym.OR,        yytext()); }
"=="        { return new_symbol(sym.EQ,        yytext()); }
"!="        { return new_symbol(sym.NEQ,       yytext()); }
">"         { return new_symbol(sym.GT,        yytext()); }
"<"         { return new_symbol(sym.LT,        yytext()); }
">="        { return new_symbol(sym.GEQ,       yytext()); }
"<="        { return new_symbol(sym.LEQ,       yytext()); }
";"         { return new_symbol(sym.SEMI,      yytext()); }
","         { return new_symbol(sym.COMMA,     yytext()); }
"("         { return new_symbol(sym.LPAREN,    yytext()); }
")"         { return new_symbol(sym.RPAREN,    yytext()); }
"{"         { return new_symbol(sym.LBRACE,    yytext()); }
"}"         { return new_symbol(sym.RBRACE,    yytext()); }
"["         { return new_symbol(sym.LBRACKET,  yytext()); }
"]"         { return new_symbol(sym.RBRACKET,  yytext()); }

"//"             { yybegin(COMMENT); }
<COMMENT> .      { yybegin(COMMENT); }
<COMMENT> "\r\n" { yybegin(YYINITIAL); }

[0-9]+                      { return new_symbol(sym.NUMBER,   new Integer(yytext())); }
"'"."'"                     { return new_symbol(sym.CHARLIT,  new Character(yytext().charAt(1))); }
("true" | "false")          { return new_symbol(sym.BOOLLIT, "true".equals(yytext()) ? 1 : 0); }
([a-z]|[A-Z])[a-zA-Z0-9_]*  { return new_symbol(sym.IDENT,    yytext()); }

. { System.err.println("Leksicka greska (" + yytext() + ") u liniji " + (yyline + 1)); }
