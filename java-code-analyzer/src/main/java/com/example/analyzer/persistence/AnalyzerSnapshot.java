package com.example.analyzer.persistence;

import com.example.analyzer.model.PackageInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.SpringComponentGraph;
import com.example.analyzer.model.TypeInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable bundle of {@link ProjectModel} + {@link SpringComponentGraph} for offline CLI use
 * without re-scanning sources at startup.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyzerSnapshot {

    public static final int FORMAT_VERSION = 1;

    private int formatVersion = FORMAT_VERSION;
    private long savedAtMillis = System.currentTimeMillis();
    private String projectRoot;
    private List<TypeInfo> types = new ArrayList<>();
    private List<PackageRecord> packages = new ArrayList<>();
    private Map<String, List<String>> packageImportDependencies = new LinkedHashMap<>();
    private SpringComponentGraph springComponentGraph = new SpringComponentGraph();

    public AnalyzerSnapshot() {
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    public long getSavedAtMillis() {
        return savedAtMillis;
    }

    public void setSavedAtMillis(long savedAtMillis) {
        this.savedAtMillis = savedAtMillis;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public List<TypeInfo> getTypes() {
        return types;
    }

    public void setTypes(List<TypeInfo> types) {
        this.types = types != null ? types : new ArrayList<>();
    }

    public List<PackageRecord> getPackages() {
        return packages;
    }

    public void setPackages(List<PackageRecord> packages) {
        this.packages = packages != null ? packages : new ArrayList<>();
    }

    public Map<String, List<String>> getPackageImportDependencies() {
        return packageImportDependencies;
    }

    public void setPackageImportDependencies(Map<String, List<String>> packageImportDependencies) {
        this.packageImportDependencies = packageImportDependencies != null ? packageImportDependencies : new LinkedHashMap<>();
    }

    public SpringComponentGraph getSpringComponentGraph() {
        return springComponentGraph;
    }

    public void setSpringComponentGraph(SpringComponentGraph springComponentGraph) {
        this.springComponentGraph = springComponentGraph != null ? springComponentGraph : new SpringComponentGraph();
    }

    public static AnalyzerSnapshot capture(ProjectModel model, SpringComponentGraph springGraph) {
        AnalyzerSnapshot s = new AnalyzerSnapshot();
        s.setFormatVersion(FORMAT_VERSION);
        s.setSavedAtMillis(System.currentTimeMillis());
        s.setProjectRoot(model.getProjectRoot());
        List<TypeInfo> typeList = new ArrayList<>(model.getTypesByQualifiedName().values());
        typeList.sort(Comparator.comparing(TypeInfo::getQualifiedName));
        s.setTypes(typeList);
        List<PackageRecord> prs = new ArrayList<>();
        for (PackageInfo pi : model.getPackages().values()) {
            PackageRecord pr = new PackageRecord();
            pr.setName(pi.getName() != null ? pi.getName() : "");
            pr.setTypeQualifiedNames(new ArrayList<>(pi.getTypeQualifiedNames()));
            prs.add(pr);
        }
        prs.sort(Comparator.comparing(PackageRecord::getName, Comparator.nullsFirst(String::compareTo)));
        s.setPackages(prs);
        for (var e : model.getPackageImportDependencies().entrySet()) {
            s.getPackageImportDependencies().put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        s.setSpringComponentGraph(springGraph);
        return s;
    }

    public ProjectModel toProjectModel() {
        ProjectModel m = new ProjectModel();
        m.setProjectRoot(projectRoot);
        for (TypeInfo t : types) {
            if (t.getQualifiedName() != null) {
                m.getTypesByQualifiedName().put(t.getQualifiedName(), t);
            }
        }
        m.getPackages().clear();
        for (PackageRecord pr : packages) {
            String pkgName = pr.getName() != null ? pr.getName() : "";
            PackageInfo pi = new PackageInfo(pkgName);
            if (pr.getTypeQualifiedNames() != null) {
                pi.getTypeQualifiedNames().addAll(pr.getTypeQualifiedNames());
            }
            m.getPackages().put(pkgName, pi);
        }
        if (packageImportDependencies != null) {
            for (var e : packageImportDependencies.entrySet()) {
                String from = e.getKey() != null ? e.getKey() : "";
                List<String> targets = e.getValue();
                if (targets == null) {
                    continue;
                }
                for (String to : targets) {
                    if (to != null) {
                        m.addPackageImportDependency(from, to);
                    }
                }
            }
        }
        return m;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageRecord {
        private String name;
        private List<String> typeQualifiedNames = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTypeQualifiedNames() {
            return typeQualifiedNames;
        }

        public void setTypeQualifiedNames(List<String> typeQualifiedNames) {
            this.typeQualifiedNames = typeQualifiedNames != null ? typeQualifiedNames : new ArrayList<>();
        }
    }
}
