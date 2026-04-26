package com.example.analyzer.seq;

import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.TypeInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a PlantUML sequence diagram from a public or protected entry method: in-project calls,
 * public/protected callees (private excluded), activate/deactivate lifelines, optional fluent chains
 * when return types resolve from the model, default max depth 10.
 */
public final class SequenceDiagramGenerator {

    private static final String ACTOR_ID = "__actor__";

    private final ProjectModel model;
    private final Map<String, CompilationUnit> cuByPath = new LinkedHashMap<>();
    private Set<ObjectCreationExpr> objectCreationsEmitted;
    /** Avoid expanding the same lambda twice (deferred + sweep). */
    private Set<LambdaExpr> expandedLambdas;

    public SequenceDiagramGenerator(ProjectModel model) {
        this.model = model;
    }

    public String generate(String qualifiedClassName, String methodName, int maxDepth) {
        return generate(qualifiedClassName, methodName, maxDepth, null);
    }

    /**
     * @param entryParameterTypes parameter types for the entry method (from {@link MethodInfo}),
     *                            or {@code null}/empty to pick the first public/protected overload by name
     */
    public String generate(String qualifiedClassName, String methodName, int maxDepth, List<String> entryParameterTypes) {
        objectCreationsEmitted = Collections.newSetFromMap(new IdentityHashMap<>());
        expandedLambdas = Collections.newSetFromMap(new IdentityHashMap<>());
        TypeInfo entry = model.getType(qualifiedClassName);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown type: " + qualifiedClassName);
        }
        if (!entry.hasSequenceTraceableMethod(methodName)) {
            throw new IllegalArgumentException(
                "No public or protected method \"" + methodName + "\" on " + qualifiedClassName
            );
        }

        CompilationUnit entryCu = compilationUnitFor(entry);
        MethodDeclaration entryMd = findTraceableMethod(entry, methodName, entryCu, entryParameterTypes)
            .orElseThrow(() -> new IllegalArgumentException("Method not found in source: " + methodName));

        List<SeqStep> steps = new ArrayList<>();
        Set<String> participants = new LinkedHashSet<>();
        participants.add(ACTOR_ID);
        participants.add(entry.getQualifiedName());

        Map<String, String> locals = localTypesFromParameters(entryMd);
        Deque<String> stack = new ArrayDeque<>();

        steps.add(new ArrowStep(ACTOR_ID, entry.getQualifiedName(), methodName));
        steps.add(new ActivateStep(entry.getQualifiedName()));

        walkMethodBody(entry, entryMd, entryCu, locals, 0, maxDepth, stack, steps, participants);

        steps.add(new ReturnStep(entry.getQualifiedName(), ACTOR_ID));
        steps.add(new DeactivateStep(entry.getQualifiedName()));
        steps.add(new ReturnKeywordStep());

        return toPlantUml(participants, steps);
    }

    private void walkMethodBody(
        TypeInfo currentType,
        MethodDeclaration md,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        String frame = recursionFrameKey(currentType, md);
        if (stack.contains(frame)) {
            return;
        }
        stack.push(frame);
        try {
            md.getBody().ifPresent(b -> walkBlock(currentType, b, cu, locals, depth, maxDepth, stack, steps, participants));
        } finally {
            stack.pop();
        }
    }

    private void walkBlock(
        TypeInfo currentType,
        BlockStmt block,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        Map<String, String> env = new LinkedHashMap<>(locals);
        for (Statement st : block.getStatements()) {
            walkStatement(currentType, st, cu, env, depth, maxDepth, stack, steps, participants);
        }
    }

    private void walkStatement(
        TypeInfo currentType,
        Statement st,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        if (st == null) {
            return;
        }
        if (st.isBlockStmt()) {
            walkBlock(currentType, st.asBlockStmt(), cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (st.isIfStmt()) {
            IfStmt i = st.asIfStmt();
            walkStatement(currentType, i.getThenStmt(), cu, locals, depth, maxDepth, stack, steps, participants);
            i.getElseStmt().ifPresent(e -> walkStatement(currentType, e, cu, locals, depth, maxDepth, stack, steps, participants));
            return;
        }
        if (st.isWhileStmt()) {
            walkStatement(currentType, st.asWhileStmt().getBody(), cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (st.isForStmt()) {
            ForStmt f = st.asForStmt();
            f.getInitialization().forEach(e -> processExpression(currentType, e, cu, locals, depth, maxDepth, stack, steps, participants));
            f.getUpdate().forEach(e -> processExpression(currentType, e, cu, locals, depth, maxDepth, stack, steps, participants));
            walkStatement(currentType, f.getBody(), cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (st.isForEachStmt()) {
            ForEachStmt fe = st.asForEachStmt();
            fe.getVariable().getVariables().forEach(v -> {
                String simple = simpleTypeName(fe.getVariable().getCommonType());
                if (simple != null && !isPrimitiveOrVoid(simple)) {
                    locals.put(v.getNameAsString(), simple);
                }
            });
            processExpression(currentType, fe.getIterable(), cu, locals, depth, maxDepth, stack, steps, participants);
            walkStatement(currentType, fe.getBody(), cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (st.isTryStmt()) {
            TryStmt t = st.asTryStmt();
            walkBlock(currentType, t.getTryBlock(), cu, locals, depth, maxDepth, stack, steps, participants);
            t.getCatchClauses().forEach(c -> walkBlock(currentType, c.getBody(), cu, locals, depth, maxDepth, stack, steps, participants));
            t.getFinallyBlock().ifPresent(fb -> walkBlock(currentType, fb, cu, locals, depth, maxDepth, stack, steps, participants));
            return;
        }
        if (st.isSwitchStmt()) {
            SwitchStmt sw = st.asSwitchStmt();
            sw.getEntries().forEach(ent -> walkBlock(currentType, new BlockStmt(ent.getStatements()), cu, locals, depth, maxDepth, stack, steps, participants));
            return;
        }
        if (st.isExpressionStmt()) {
            processExpression(currentType, st.asExpressionStmt().getExpression(), cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (st.isReturnStmt()) {
            st.asReturnStmt().getExpression().ifPresent(e -> processExpression(currentType, e, cu, locals, depth, maxDepth, stack, steps, participants));
            return;
        }
        if (st.isThrowStmt()) {
            Expression thrown = st.asThrowStmt().getExpression();
            if (thrown != null) {
                processExpression(currentType, thrown, cu, locals, depth, maxDepth, stack, steps, participants);
            }
        }
    }

    private void processExpression(
        TypeInfo currentType,
        Expression expr,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        if (expr == null) {
            return;
        }
        if (expr.isLambdaExpr()) {
            processLambdaExpression(currentType, expr.asLambdaExpr(), cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (expr.isObjectCreationExpr()) {
            handleObjectCreation(currentType, expr.asObjectCreationExpr(), cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (expr.isMethodCallExpr()) {
            MethodCallExpr mc = expr.asMethodCallExpr();
            if (!outermostChained(mc).equals(mc)) {
                return;
            }
            processMethodChain(currentType, mc, cu, locals, depth, maxDepth, stack, steps, participants);
            return;
        }
        if (expr.isVariableDeclarationExpr()) {
            VariableDeclarationExpr vde = expr.asVariableDeclarationExpr();
            String stype = simpleTypeName(vde.getCommonType());
            for (var v : vde.getVariables()) {
                v.getInitializer().ifPresent(init -> processExpression(currentType, init, cu, locals, depth, maxDepth, stack, steps, participants));
                if (stype != null && !isPrimitiveOrVoid(stype)) {
                    locals.put(v.getNameAsString(), stype);
                }
            }
            return;
        }
        for (Node child : expr.getChildNodes()) {
            if (child instanceof Expression sub) {
                processExpression(currentType, sub, cu, locals, depth, maxDepth, stack, steps, participants);
            }
        }
    }

    private static MethodCallExpr outermostChained(MethodCallExpr mc) {
        Node n = mc;
        while (n.getParentNode().orElse(null) instanceof MethodCallExpr p) {
            n = p;
        }
        return (MethodCallExpr) n;
    }

    /** Innermost (first to execute) to outermost — reverse of scope chain. */
    private static List<MethodCallExpr> chainExecutionOrder(MethodCallExpr outer) {
        List<MethodCallExpr> innerToOuter = new ArrayList<>();
        MethodCallExpr c = outer;
        while (true) {
            innerToOuter.add(c);
            if (c.getScope().isEmpty() || !(c.getScope().get() instanceof MethodCallExpr)) {
                break;
            }
            c = c.getScope().get().asMethodCallExpr();
        }
        Collections.reverse(innerToOuter);
        return innerToOuter;
    }

    private void processMethodChain(
        TypeInfo currentType,
        MethodCallExpr outer,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        List<MethodCallExpr> order = chainExecutionOrder(outer);
        TypeInfo inferredReceiver = currentType;
        for (MethodCallExpr link : order) {
            for (Expression arg : link.getArguments()) {
                if (arg.isLambdaExpr()) {
                    continue;
                }
                processExpression(currentType, arg, cu, locals, depth, maxDepth, stack, steps, participants);
            }
            Optional<TypeInfo> recv = resolveChainLinkReceiver(currentType, link, cu, locals, inferredReceiver);
            if (recv.isEmpty()) {
                break;
            }
            TypeInfo toType = recv.get();
            String name = link.getNameAsString();
            if (!toType.hasSequenceTraceableMethod(name)) {
                Optional<TypeInfo> next = inferReturnType(toType, name, link.getArguments().size(), currentType, cu);
                inferredReceiver = next.orElse(inferredReceiver);
                continue;
            }
            emitCallAndRecurse(
                currentType, toType, name, cu, locals, depth, maxDepth, stack, steps, participants, link
            );
            Optional<TypeInfo> next = inferReturnType(toType, name, link.getArguments().size(), currentType, cu);
            inferredReceiver = next.orElse(toType);
        }
        // If an early link failed recv (e.g. Observation not in model), we still need lambdas on later links
        // such as observe(() -> { ... }).
        for (MethodCallExpr link : order) {
            expandLambdasOnly(currentType, link, cu, locals, depth, maxDepth, stack, steps, participants);
        }
    }

    /**
     * After a method call in a chain, the next receiver is often the return type (e.g. {@code Event.builder()} → {@code EventBuilder}).
     */
    private Optional<TypeInfo> inferReturnType(
        TypeInfo declaringType,
        String methodName,
        int argCount,
        TypeInfo contextType,
        CompilationUnit cu
    ) {
        MethodInfo mi = findMethodInfoByArity(declaringType, methodName, argCount);
        if (mi == null) {
            return Optional.empty();
        }
        String rt = stripGenerics(mi.getReturnType());
        if (rt.isEmpty() || rt.equals("void")) {
            return Optional.empty();
        }
        rt = unwrapOptionalWrapper(rt);
        return resolveTypeName(firstSimpleToken(rt), contextType, cu);
    }

    private static String unwrapOptionalWrapper(String rt) {
        String s = rt.trim();
        if (s.startsWith("Optional<") && s.endsWith(">")) {
            return s.substring("Optional<".length(), s.length() - 1).trim();
        }
        return s;
    }

    private static String firstSimpleToken(String returnTypeFragment) {
        String s = returnTypeFragment;
        int lt = s.indexOf('<');
        if (lt > 0) {
            s = s.substring(0, lt);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        return s.trim();
    }

    private static MethodInfo findMethodInfoByArity(TypeInfo type, String name, int argCount) {
        for (MethodInfo m : type.getPublicMethods()) {
            if (name.equals(m.getName()) && m.getParameterTypes().size() == argCount) {
                return m;
            }
        }
        for (MethodInfo m : type.getProtectedMethods()) {
            if (name.equals(m.getName()) && m.getParameterTypes().size() == argCount) {
                return m;
            }
        }
        return null;
    }

    private Optional<TypeInfo> resolveChainLinkReceiver(
        TypeInfo lexicalType,
        MethodCallExpr link,
        CompilationUnit cu,
        Map<String, String> locals,
        TypeInfo inferredReceiver
    ) {
        Optional<Expression> scopeOpt = link.getScope();
        if (scopeOpt.isEmpty()) {
            return Optional.of(inferredReceiver);
        }
        Expression sc = scopeOpt.get();
        if (sc instanceof ThisExpr) {
            return Optional.of(inferredReceiver);
        }
        if (sc instanceof MethodCallExpr) {
            return Optional.of(inferredReceiver);
        }
        return resolveReceiverType(lexicalType, sc, cu, locals);
    }

    /**
     * Lambda bodies passed to e.g. {@code observe(() -> { ... })} run after the callee is entered; defer walking them
     * until after {@code activate} on the callee so the diagram nests correctly. Skipped in the pre-call arg pass.
     */
    private void processDeferredLambdaArguments(
        TypeInfo currentType,
        MethodCallExpr callSite,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        for (Expression arg : callSite.getArguments()) {
            unwrapToLambda(arg).ifPresent(lam -> processLambdaExpression(
                currentType, lam, cu, locals, depth, maxDepth, stack, steps, participants
            ));
        }
    }

    /** When a call is not traced (external / no matching method), still walk lambda args so inner project calls appear. */
    private void expandLambdasOnly(
        TypeInfo currentType,
        MethodCallExpr link,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        for (Expression arg : link.getArguments()) {
            unwrapToLambda(arg).ifPresent(lam -> processLambdaExpression(
                currentType, lam, cu, locals, depth, maxDepth, stack, steps, participants
            ));
        }
    }

    /** Strip parentheses / casts so {@code ((Runnable) () -> {})} is recognized. */
    private static Optional<LambdaExpr> unwrapToLambda(Expression e) {
        Expression x = e;
        while (true) {
            if (x.isLambdaExpr()) {
                return Optional.of(x.asLambdaExpr());
            }
            if (x.isEnclosedExpr()) {
                x = x.asEnclosedExpr().getInner();
                continue;
            }
            if (x.isCastExpr()) {
                x = x.asCastExpr().getExpression();
                continue;
            }
            return Optional.empty();
        }
    }

    private void processLambdaExpression(
        TypeInfo currentType,
        LambdaExpr lam,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        if (!expandedLambdas.add(lam)) {
            return;
        }
        Map<String, String> lamEnv = extendLocalsForLambda(lam, locals);
        if (lam.getExpressionBody().isPresent()) {
            processExpression(
                currentType, lam.getExpressionBody().get(), cu, lamEnv, depth, maxDepth, stack, steps, participants
            );
            return;
        }
        Statement st = lam.getBody();
        if (st == null) {
            return;
        }
        if (st.isBlockStmt()) {
            walkBlock(currentType, st.asBlockStmt(), cu, lamEnv, depth, maxDepth, stack, steps, participants);
        } else if (st.isExpressionStmt()) {
            processExpression(
                currentType, st.asExpressionStmt().getExpression(), cu, lamEnv, depth, maxDepth, stack, steps, participants
            );
        } else {
            walkStatement(currentType, st, cu, lamEnv, depth, maxDepth, stack, steps, participants);
        }
    }

    private static Map<String, String> extendLocalsForLambda(LambdaExpr lam, Map<String, String> locals) {
        Map<String, String> lamEnv = new LinkedHashMap<>(locals);
        for (Parameter p : lam.getParameters()) {
            if (p.getType().isUnknownType()) {
                continue;
            }
            String s = simpleTypeName(p.getType());
            if (s != null && !isPrimitiveOrVoid(s)) {
                lamEnv.put(p.getNameAsString(), s);
            }
        }
        return lamEnv;
    }

    private void emitCallAndRecurse(
        TypeInfo currentType,
        TypeInfo toType,
        String calleeName,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants,
        MethodCallExpr callSite
    ) {
        String fromId = currentType.getQualifiedName();
        String toId = toType.getQualifiedName();
        steps.add(new ArrowStep(fromId, toId, calleeName));
        steps.add(new ActivateStep(toId));
        participants.add(fromId);
        participants.add(toId);

        processDeferredLambdaArguments(
            currentType, callSite, cu, locals, depth, maxDepth, stack, steps, participants
        );

        if (depth + 1 >= maxDepth) {
            steps.add(new ReturnStep(toId, fromId));
            steps.add(new DeactivateStep(toId));
            return;
        }
        CompilationUnit toCu = compilationUnitFor(toType);
        Optional<MethodDeclaration> toMd = findTraceableMethod(toType, calleeName, toCu, null);
        if (toMd.isEmpty()) {
            steps.add(new ReturnStep(toId, fromId));
            steps.add(new DeactivateStep(toId));
            return;
        }
        Map<String, String> nextLocals = localTypesFromParameters(toMd.get());
        walkMethodBody(toType, toMd.get(), toCu, nextLocals, depth + 1, maxDepth, stack, steps, participants);
        steps.add(new ReturnStep(toId, fromId));
        steps.add(new DeactivateStep(toId));
    }

    private void handleObjectCreation(
        TypeInfo currentType,
        ObjectCreationExpr oce,
        CompilationUnit cu,
        Map<String, String> locals,
        int depth,
        int maxDepth,
        Deque<String> stack,
        List<SeqStep> steps,
        Set<String> participants
    ) {
        if (!oce.getAnonymousClassBody().isEmpty() || objectCreationsEmitted.contains(oce)) {
            return;
        }
        Type t = oce.getType();
        if (!(t instanceof ClassOrInterfaceType cit)) {
            return;
        }
        String simple = cit.getNameAsString().replaceAll("<.*>", "").trim();
        Optional<TypeInfo> toType = resolveTypeName(simple, currentType, cu);
        if (toType.isEmpty()) {
            for (Expression arg : oce.getArguments()) {
                processExpression(currentType, arg, cu, locals, depth, maxDepth, stack, steps, participants);
            }
            return;
        }
        objectCreationsEmitted.add(oce);
        for (Expression arg : oce.getArguments()) {
            processExpression(currentType, arg, cu, locals, depth, maxDepth, stack, steps, participants);
        }
        TypeInfo tgt = toType.get();
        String fromId = currentType.getQualifiedName();
        String toId = tgt.getQualifiedName();
        participants.add(toId);
        steps.add(new CreateTypeStep(toId));
        steps.add(new ArrowStep(fromId, toId, "new"));
        steps.add(new ActivateStep(toId));
        steps.add(new ReturnStep(toId, fromId));
        steps.add(new DeactivateStep(toId));
    }

    private Optional<TypeInfo> resolveReceiverType(
        TypeInfo currentType,
        Expression scope,
        CompilationUnit cu,
        Map<String, String> locals
    ) {
        if (scope == null) {
            return Optional.of(currentType);
        }
        if (scope.isThisExpr()) {
            return Optional.of(currentType);
        }
        if (scope.isClassExpr()) {
            Type st = scope.asClassExpr().getType();
            if (st instanceof ClassOrInterfaceType cit) {
                return resolveTypeName(cit.getNameAsString().replaceAll("<.*>", "").trim(), currentType, cu);
            }
        }
        if (scope.isNameExpr()) {
            String name = scope.asNameExpr().getNameAsString();
            String simple = locals.get(name);
            if (simple != null) {
                return resolveTypeName(simple, currentType, cu);
            }
            String fieldType = currentType.getFieldsByName().get(name);
            if (fieldType != null) {
                return resolveTypeName(fieldType, currentType, cu);
            }
            // e.g. static call TypeName.method() parsed as NameExpr scope
            return resolveTypeName(name, currentType, cu);
        }
        if (scope.isFieldAccessExpr()) {
            FieldAccessExpr fa = scope.asFieldAccessExpr();
            if (fa.getScope().isThisExpr()) {
                String field = fa.getNameAsString();
                String ft = currentType.getFieldsByName().get(field);
                if (ft == null) {
                    return Optional.empty();
                }
                return resolveTypeName(ft, currentType, cu);
            }
            if (fa.getScope().isNameExpr()) {
                String base = fa.getScope().asNameExpr().getNameAsString();
                String baseSimple = locals.get(base);
                if (baseSimple == null) {
                    baseSimple = currentType.getFieldsByName().get(base);
                }
                if (baseSimple == null) {
                    return Optional.empty();
                }
                Optional<TypeInfo> baseType = resolveTypeName(baseSimple, currentType, cu);
                if (baseType.isEmpty()) {
                    return Optional.empty();
                }
                String ft = baseType.get().getFieldsByName().get(fa.getNameAsString());
                if (ft == null) {
                    return Optional.empty();
                }
                return resolveTypeName(ft, currentType, cu);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<TypeInfo> resolveTypeName(String simpleName, TypeInfo contextType, CompilationUnit cu) {
        if (simpleName == null || simpleName.isEmpty()) {
            return Optional.empty();
        }
        String imported = resolveImportedFqn(simpleName, cu);
        if (imported != null) {
            TypeInfo t = model.getType(imported);
            if (t != null) {
                return Optional.of(t);
            }
        }
        List<TypeInfo> cands = model.getTypesBySimpleName(simpleName);
        if (cands.isEmpty()) {
            return Optional.empty();
        }
        if (cands.size() == 1) {
            return Optional.of(cands.get(0));
        }
        String pkg = contextType.getPackageName() != null ? contextType.getPackageName() : "";
        List<TypeInfo> same = new ArrayList<>();
        for (TypeInfo t : cands) {
            if (pkg.equals(t.getPackageName() != null ? t.getPackageName() : "")) {
                same.add(t);
            }
        }
        if (same.size() == 1) {
            return Optional.of(same.get(0));
        }
        return Optional.empty();
    }

    private static String resolveImportedFqn(String simpleName, CompilationUnit cu) {
        for (var imp : cu.getImports()) {
            if (imp.isStatic() || imp.isAsterisk()) {
                continue;
            }
            String name = imp.getNameAsString();
            if (name.endsWith("." + simpleName)) {
                return name;
            }
        }
        return null;
    }

    private static Map<String, String> localTypesFromParameters(MethodDeclaration md) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Parameter p : md.getParameters()) {
            String s = simpleTypeName(p.getType());
            if (s != null && !isPrimitiveOrVoid(s)) {
                m.put(p.getNameAsString(), s);
            }
        }
        return m;
    }

    private static String simpleTypeName(Type t) {
        if (t == null) {
            return null;
        }
        if (t instanceof ClassOrInterfaceType cit) {
            return cit.getNameAsString().replaceAll("<.*>", "").trim();
        }
        String s = t.asString();
        return s.replaceAll("<[^>]*>", "").trim();
    }

    private static boolean isPrimitiveOrVoid(String s) {
        return switch (s) {
            case "void", "int", "long", "short", "byte", "char", "boolean", "float", "double" -> true;
            default -> false;
        };
    }

    private CompilationUnit compilationUnitFor(TypeInfo type) {
        String path = type.getSourcePath();
        if (path == null) {
            throw new IllegalStateException("No source path for " + type.getQualifiedName());
        }
        return cuByPath.computeIfAbsent(path, p -> {
            try {
                return StaticJavaParser.parse(Path.of(p));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String recursionFrameKey(TypeInfo type, MethodDeclaration md) {
        String params = md.getParameters().stream()
            .map(p -> stripGenerics(p.getType().toString()))
            .collect(Collectors.joining(","));
        return type.getQualifiedName() + "#" + md.getNameAsString() + "(" + params + ")";
    }

    private static Optional<MethodDeclaration> findTraceableMethod(
        TypeInfo type,
        String name,
        CompilationUnit cu,
        List<String> parameterTypes
    ) {
        Optional<TypeDeclaration<?>> td = cu.getTypes().stream()
            .filter(t -> t.getNameAsString().equals(type.getSimpleName()))
            .findFirst();
        if (td.isEmpty()) {
            return Optional.empty();
        }
        var stream = td.get().getMethodsByName(name).stream()
            .filter(m -> m.isPublic() || m.isProtected());
        if (parameterTypes != null && !parameterTypes.isEmpty()) {
            stream = stream.filter(m -> parametersMatchDeclaration(m, parameterTypes));
        }
        return stream.findFirst();
    }

    private static boolean parametersMatchDeclaration(MethodDeclaration md, List<String> expected) {
        if (md.getParameters().size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            String ast = stripGenerics(md.getParameter(i).getType().toString());
            String exp = stripGenerics(expected.get(i));
            if (!ast.equals(exp) && !normalizeSimple(ast).equals(normalizeSimple(exp))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeSimple(String typeName) {
        if (typeName == null) {
            return "";
        }
        int dot = typeName.lastIndexOf('.');
        return dot < 0 ? typeName : typeName.substring(dot + 1);
    }

    private static String stripGenerics(String typeName) {
        if (typeName == null) {
            return "";
        }
        return typeName.replaceAll("<[^>]*>", "").trim();
    }

    private String toPlantUml(Set<String> participantIds, List<SeqStep> steps) {
        Map<String, String> display = participantDisplayLabels(participantIds);
        Map<String, String> alias = participantAliases(participantIds, display);
        List<String> orderedIds = new ArrayList<>(participantIds);
        orderedIds.sort((a, b) -> {
            if (ACTOR_ID.equals(a)) {
                return -1;
            }
            if (ACTOR_ID.equals(b)) {
                return 1;
            }
            return display.get(a).compareTo(display.get(b));
        });

        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("autonumber\n");
        for (String id : orderedIds) {
            String d = display.get(id);
            String a = alias.get(id);
            sb.append("participant \"").append(escapeQuoted(d)).append("\" as ").append(a).append("\n");
        }
        for (SeqStep step : steps) {
            if (step instanceof ArrowStep ar) {
                sb.append(alias.get(ar.fromId()))
                    .append(" -> ")
                    .append(alias.get(ar.toId()))
                    .append(": ")
                    .append(escapeLabel(ar.label()))
                    .append("\n");
            } else if (step instanceof ActivateStep ac) {
                sb.append("activate ").append(alias.get(ac.participantId())).append("\n");
            } else if (step instanceof DeactivateStep de) {
                sb.append("deactivate ").append(alias.get(de.participantId())).append("\n");
            } else if (step instanceof CreateTypeStep cr) {
                sb.append("create ").append(alias.get(cr.typeId())).append("\n");
            } else if (step instanceof ReturnStep re) {
                sb.append(alias.get(re.fromId()))
                    .append(" --> ")
                    .append(alias.get(re.toId()))
                    .append("\n");
            } else if (step instanceof ReturnKeywordStep) {
                sb.append("return\n");
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    private Map<String, String> participantDisplayLabels(Set<String> participantIds) {
        Map<String, Integer> simpleCount = new LinkedHashMap<>();
        for (String id : participantIds) {
            if (ACTOR_ID.equals(id)) {
                continue;
            }
            TypeInfo t = model.getType(id);
            if (t != null) {
                String s = t.getSimpleName();
                simpleCount.merge(s, 1, Integer::sum);
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : participantIds) {
            if (ACTOR_ID.equals(id)) {
                out.put(id, "Actor");
                continue;
            }
            TypeInfo t = model.getType(id);
            if (t == null) {
                out.put(id, id);
                continue;
            }
            String simple = t.getSimpleName();
            if (simpleCount.getOrDefault(simple, 0) > 1) {
                out.put(id, t.getQualifiedName());
            } else {
                out.put(id, simple);
            }
        }
        return out;
    }

    private static Map<String, String> participantAliases(Set<String> participantIds, Map<String, String> display) {
        Map<String, String> alias = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        for (String id : participantIds) {
            String base = "P_" + sanitizeAliasBase(display.get(id));
            String a = base;
            int i = 0;
            while (used.contains(a)) {
                a = base + "_" + (++i);
            }
            used.add(a);
            alias.put(id, a);
        }
        return alias;
    }

    private static String sanitizeAliasBase(String display) {
        String s = display.replace('.', '_').replace('$', '_').replace(' ', '_');
        if (s.isBlank()) {
            return "X";
        }
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            s = "T_" + s;
        }
        return s;
    }

    private static String escapeQuoted(String s) {
        return s.replace("\"", "'");
    }

    private static String escapeLabel(String s) {
        return s.replace("\n", " ");
    }

    private sealed interface SeqStep permits ArrowStep, ActivateStep, DeactivateStep, CreateTypeStep, ReturnStep, ReturnKeywordStep { }

    private record ArrowStep(String fromId, String toId, String label) implements SeqStep { }

    private record ActivateStep(String participantId) implements SeqStep { }

    private record DeactivateStep(String participantId) implements SeqStep { }

    private record CreateTypeStep(String typeId) implements SeqStep { }

    private record ReturnStep(String fromId, String toId) implements SeqStep { }

    /** Top-level return matching common PlantUML style after entry lifeline closes. */
    private record ReturnKeywordStep() implements SeqStep { }
}
