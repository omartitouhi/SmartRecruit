from app.services.skills_catalog import extract_skills, skill_key


def test_aliases_resolve_to_the_canonical_name_in_order_of_appearance():
    assert extract_skills("Stack : Postgres, k8s et Docker Compose") == ["PostgreSQL", "Kubernetes", "Docker"]


def test_a_skill_contained_in_another_is_not_counted_twice():
    assert extract_skills("Spring Boot") == ["Spring Boot"]
    assert extract_skills("Spring MVC et Spring Boot") == ["Spring", "Spring Boot"]


def test_symbols_are_kept_and_short_names_need_their_exact_spelling():
    assert extract_skills("C++, C# et Node.js") == ["C++", "C#", "Node.js"]
    assert extract_skills("on va go vers le rest du monde") == []
    assert extract_skills("Go et REST") == ["Go", "REST"]


def test_a_word_inside_another_is_not_a_skill():
    assert extract_skills("Javascript, pas de javanais ni de reactions") == ["JavaScript"]


def test_skill_key_makes_variants_comparable():
    assert skill_key("Postgres") == skill_key("PostgreSQL")
    assert skill_key("K8s") == skill_key("Kubernetes")
    assert skill_key("Cucumber") == "cucumber"
