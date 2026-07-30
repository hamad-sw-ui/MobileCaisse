#!/usr/bin/env python3
"""
État persistant du pipeline — permet la reprise après interruption.

**Intervention humaine supprimée** : après un échec en étape 4, il fallait tout
relancer depuis l'étape 1, y compris la construction de l'image Docker
(5-10 min). Le moteur reprend désormais là où il s'est arrêté.
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from datetime import datetime
from enum import Enum
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE_FILE = ROOT / "software-factory" / "cache" / "pipeline-state.json"


class Status(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    OK = "ok"
    FAILED = "failed"
    SKIPPED = "skipped"


@dataclass
class StepState:
    name: str
    status: str = Status.PENDING
    started: str = ""
    finished: str = ""
    duration: float = 0.0
    exit_code: int | None = None
    detail: str = ""
    blocker: str = ""
    log_file: str = ""
    attempts: int = 0

    @property
    def done(self) -> bool:
        return self.status in (Status.OK, Status.SKIPPED)


@dataclass
class PipelineState:
    stamp: str = ""
    commit: str = ""
    strategy: str = "none"
    started: str = ""
    finished: str = ""
    steps: dict[str, StepState] = field(default_factory=dict)
    iteration: int = 0
    autofix_applied: int = 0

    # --------------------------------------------------------------- accès

    def step(self, name: str) -> StepState:
        if name not in self.steps:
            self.steps[name] = StepState(name=name)
        return self.steps[name]

    @property
    def failed_step(self) -> str | None:
        for s in self.steps.values():
            if s.status == Status.FAILED:
                return s.name
        return None

    @property
    def success(self) -> bool:
        return bool(self.steps) and self.failed_step is None

    # ------------------------------------------------------- persistance

    def save(self) -> None:
        STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
        payload = asdict(self)
        payload["steps"] = {k: asdict(v) for k, v in self.steps.items()}
        STATE_FILE.write_text(json.dumps(payload, indent=2, ensure_ascii=False),
                              encoding="utf-8")

    @classmethod
    def load(cls) -> "PipelineState | None":
        if not STATE_FILE.exists():
            return None
        try:
            raw = json.loads(STATE_FILE.read_text(encoding="utf-8"))
            steps = {k: StepState(**v) for k, v in raw.pop("steps", {}).items()}
            st = cls(**raw)
            st.steps = steps
            return st
        except Exception:
            # Un état corrompu ne doit jamais bloquer un cycle : on repart de zéro.
            return None

    @classmethod
    def fresh(cls, commit: str) -> "PipelineState":
        return cls(stamp=datetime.now().strftime("%Y-%m-%d_%H%M%S"),
                   commit=commit,
                   started=datetime.now().isoformat(timespec="seconds"))

    def clear(self) -> None:
        STATE_FILE.unlink(missing_ok=True)

    def resumable_from(self, commit: str) -> bool:
        """
        Une reprise n'est légitime que sur le **même commit** : si le code a
        changé, les étapes déjà validées ne prouvent plus rien.
        """
        return self.commit == commit and self.failed_step is not None
