package com.github.erlanp.actions;

import com.github.erlanp.utils.PsiClassUtils;
import com.github.erlanp.walle.GenerateTestCode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiClass;

public class GenerateJUnitFiveAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        final PsiClass psiClass = PsiClassUtils.getPsiClass(event);
        if (psiClass == null) {
            return;
        }
        GenerateTestCode gen = new GenerateTestCode();
        gen.junit5 = true;
        try {
            gen.run(psiClass);
        } catch (Exception exp) {
            exp.printStackTrace();
        }
    }
}
