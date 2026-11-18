-- 已登记的 PlanReport 页面。仅修改外部组件标识，保留数据库主键及其知识引用。
UPDATE knowledge_component
SET component_id = CASE component_id
    WHEN 'DetailDrawer / Glossary' THEN 'Glossary'
    WHEN 'DetailDrawer / ManagersDrawer' THEN 'ManagersDrawer'
    WHEN 'DetailDrawer / PlansDrawer' THEN 'PlansDrawer'
    WHEN 'DetailDrawer / SkillsDrawer' THEN 'SkillsDrawer'
END
WHERE page_id = '328937da8dfced87'
  AND component_id IN (
    'DetailDrawer / Glossary',
    'DetailDrawer / ManagersDrawer',
    'DetailDrawer / PlansDrawer',
    'DetailDrawer / SkillsDrawer'
  );
