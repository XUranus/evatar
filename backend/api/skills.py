"""Skills and MCP server management API."""

from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from models import get_db, Skill, MCPServer

router = APIRouter(prefix="/api", tags=["skills"])


class SkillCreate(BaseModel):
    id: str = Field(max_length=64, pattern=r"^[a-z0-9_-]+$")
    name: str = Field(max_length=128)
    description: str = ""
    system_prompt: str = Field(max_length=16384)
    icon: str = "⚡"


class SkillUpdate(BaseModel):
    name: str | None = None
    description: str | None = None
    system_prompt: str | None = None
    icon: str | None = None
    enabled: bool | None = None


DEFAULT_SKILLS = [
    {"id": "summarize", "name": "总结截图", "description": "分析最近的截图，生成摘要笔记", "system_prompt": "请帮我整理最近截图的内容，按主题分类，生成一份简洁的笔记摘要。", "icon": "📝"},
    {"id": "reminders", "name": "提取提醒", "description": "从截图中提取所有需要提醒的事项", "system_prompt": "请搜索我的截图知识库，找出所有包含时间、日期、截止时间的内容，整理成一个提醒事项列表。", "icon": "⏰"},
    {"id": "research", "name": "深度研究", "description": "对截图中的主题进行深入研究", "system_prompt": "请搜索我最近截图中关注的主题，然后用互联网搜索补充最新信息，给我一份完整的研究报告。", "icon": "🔬"},
    {"id": "stock", "name": "股票分析", "description": "整理截图中的股票和金融信息", "system_prompt": "请搜索我截图中的股票、基金、金融相关内容，结合互联网搜索最新行情，整理成投资笔记。", "icon": "📈"},
]


@router.get("/skills")
def list_skills(db: Session = Depends(get_db)):
    skills = db.query(Skill).filter(Skill.enabled == True).all()
    if not skills:
        for s in DEFAULT_SKILLS:
            db.add(Skill(**s))
        db.commit()
        skills = db.query(Skill).filter(Skill.enabled == True).all()
    return {"skills": [{"id": s.id, "name": s.name, "description": s.description, "icon": s.icon} for s in skills]}


@router.get("/skills/{skill_id}")
def get_skill(skill_id: str, db: Session = Depends(get_db)):
    skill = db.query(Skill).filter(Skill.id == skill_id).first()
    if not skill:
        raise HTTPException(status_code=404, detail="Skill not found")
    return {"id": skill.id, "name": skill.name, "description": skill.description,
            "system_prompt": skill.system_prompt, "icon": skill.icon}


@router.post("/skills")
def create_skill(body: SkillCreate, db: Session = Depends(get_db)):
    existing = db.query(Skill).filter(Skill.id == body.id).first()
    if existing:
        raise HTTPException(status_code=409, detail=f"Skill '{body.id}' already exists")
    skill = Skill(**body.model_dump())
    db.add(skill)
    db.commit()
    return {"id": skill.id}


@router.put("/skills/{skill_id}")
def update_skill(skill_id: str, body: SkillUpdate, db: Session = Depends(get_db)):
    skill = db.query(Skill).filter(Skill.id == skill_id).first()
    if not skill:
        raise HTTPException(status_code=404, detail="Skill not found")
    for k, v in body.model_dump(exclude_none=True).items():
        setattr(skill, k, v)
    db.commit()
    return {"message": "Updated"}


@router.delete("/skills/{skill_id}")
def delete_skill(skill_id: str, db: Session = Depends(get_db)):
    skill = db.query(Skill).filter(Skill.id == skill_id).first()
    if not skill:
        raise HTTPException(status_code=404, detail="Skill not found")
    db.delete(skill)
    db.commit()
    return {"message": "Deleted"}


class MCPServerCreate(BaseModel):
    id: str = Field(max_length=64, pattern=r"^[a-z0-9_-]+$")
    name: str = Field(max_length=128)
    url: str = Field(max_length=512)
    description: str = ""


@router.get("/mcp-servers")
def list_mcp_servers(db: Session = Depends(get_db)):
    servers = db.query(MCPServer).all()
    return {"servers": [
        {"id": s.id, "name": s.name, "url": s.url, "description": s.description, "enabled": s.enabled}
        for s in servers
    ]}


@router.post("/mcp-servers")
def create_mcp_server(body: MCPServerCreate, db: Session = Depends(get_db)):
    existing = db.query(MCPServer).filter(MCPServer.id == body.id).first()
    if existing:
        raise HTTPException(status_code=409, detail=f"MCP server '{body.id}' already exists")
    server = MCPServer(**body.model_dump())
    db.add(server)
    db.commit()
    return {"id": server.id}


@router.delete("/mcp-servers/{server_id}")
def delete_mcp_server(server_id: str, db: Session = Depends(get_db)):
    server = db.query(MCPServer).filter(MCPServer.id == server_id).first()
    if not server:
        raise HTTPException(status_code=404, detail="MCP server not found")
    db.delete(server)
    db.commit()
    return {"message": "Deleted"}
