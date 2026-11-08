import math

import pytest

from app.services.scoring import CRITERIA_WEIGHTS, InvalidScoreError, compute_global_score


def test_weights_sum_to_one():
    assert math.isclose(sum(CRITERIA_WEIGHTS.values()), 1.0)


def test_global_score_is_the_weighted_average_of_sub_scores():
    # 0.5 * 84 + 0.3 * 80 + 0.2 * 62.5 = 42 + 24 + 12.5 (exemple de DATA_MODEL.md §4.1)
    assert compute_global_score({"skills": 84, "experience": 80, "education": 62.5}) == 78.5


def test_global_score_is_deterministic():
    sub_scores = {"skills": 73.3, "experience": 41, "education": 99}
    assert len({compute_global_score(sub_scores) for _ in range(20)}) == 1


@pytest.mark.parametrize(
    "sub_scores, expected",
    [
        ({"skills": 0, "experience": 0, "education": 0}, 0.0),
        ({"skills": 100, "experience": 100, "education": 100}, 100.0),
    ],
)
def test_global_score_stays_between_0_and_100(sub_scores, expected):
    assert compute_global_score(sub_scores) == expected


def test_weights_are_renormalised_over_the_given_criteria():
    # (0.5 * 80 + 0.3 * 40) / 0.8
    assert compute_global_score({"skills": 80, "experience": 40}) == 65.0


@pytest.mark.parametrize("value", [-0.1, 100.5, 150, float("nan"), float("inf"), "80", None, True])
def test_out_of_range_sub_score_is_rejected_not_corrected(value):
    with pytest.raises(InvalidScoreError):
        compute_global_score({"skills": value, "experience": 50, "education": 50})


def test_unknown_criterion_is_rejected():
    with pytest.raises(InvalidScoreError):
        compute_global_score({"skills": 50, "soft_skills": 90})


def test_empty_input_is_rejected():
    with pytest.raises(InvalidScoreError):
        compute_global_score({})
