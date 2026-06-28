Analiza completamente este proyecto. Lee todos los archivos relevantes: 
CLAUDE.md existente, todos los pom.xml, todas las clases Java, 
.claude/settings.local.json, y cualquier plan o documento en la raíz.

Con ese contexto completo haz lo siguiente:

1. Reescribe CLAUDE.md con estas 7 secciones reales del proyecto:
   - ARQUITECTURA (estructura real de módulos con decisiones ya tomadas y por qué)
   - STACK TÉCNICO (versiones exactas, quirks conocidos, anti-patrones prohibidos)
   - CONTRATOS DE COMPONENTES (interfaces públicas reales entre módulos)
   - ESTÁNDARES DE CÓDIGO (patrones que ya usamos, naming conventions reales)
		--> Herramientas obligatorias: Context7: SIEMPRE usar antes de implementar con cualquier librería del stack. 
				-- Patrón: resolve library ID → get docs → implementar.
				-- Nunca asumir APIs de memoria, especialmente: Spring Boot 3.x, 
				-- Vite config, React hooks, JPA/Hibernate 6.x.
   - PROBLEMAS CONOCIDOS Y SOLUCIONES (basado en lo que ya resolvimos)
   - WORKFLOW DE DESARROLLO (cómo crear feature, cómo hacer fix, qué ejecutar antes de commit)
   - CONTEXTO DE NEGOCIO (qué hace el sistema, glosario del dominio DCF/valuación)

2. Crea LEARNINGS.md documentando decisiones técnicas ya tomadas, problemas 
   que enfrentamos y cómo los resolvimos, y quirks del stack descubiertos.

3. Crea .claude/commands/new-feature.md con el workflow TDD obligatorio 
   para este stack (Java 21, Spring Boot 3.3, Maven multi-módulo).
   - PASO 0 (antes de escribir código): Usa Context7 para verificar la API actual de cualquier librería 
que vayas a usar en esta feature. No asumas nada de memoria.

4. Crea .claude/commands/fix.md con workflow de debugging test-first.

5. Crea .claude/commands/review.md con checklist real basado en los 
   anti-patrones específicos de este proyecto.

6. Crea .claude/agents/java-reviewer.md como sub-agente especializado 
   en este stack exacto (Spring Boot, JPA, valuation-engine Java puro).

7. Crea .claude/agents/spec-writer.md como sub-agente que genera specs 
   formales antes de implementar cualquier feature.

8. Actualiza .claude/settings.local.json con hooks para:
   - PreToolUse: bloquear git commit si mvn test falla
   - PostToolUse: checkstyle después de editar archivos Java
   - Stop: notificación cuando termina una tarea

No inventes nada. Todo debe estar basado en el código y decisiones reales 
que ya existen en el proyecto.