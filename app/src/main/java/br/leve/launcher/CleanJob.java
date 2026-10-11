package br.leve.launcher;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Limpeza automática periódica. Não há serviço rodando: o próprio sistema acorda o app só na hora. */
public class CleanJob extends JobService {
    private static final int JOB_ID = 7001;

    @Override
    public boolean onStartJob(final JobParameters params) {
        Cleaner.cleanNow(this, true, new Runnable() {
            @Override public void run() { jobFinished(params, false); }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /** (Re)agenda conforme a preferência; não reinicia a contagem se nada mudou. */
    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        int minutes = new Prefs(c).interval();
        if (minutes <= 0) {
            js.cancel(JOB_ID);
            return;
        }
        long ms = minutes * 60L * 1000L;
        for (JobInfo j : js.getAllPendingJobs()) {
            if (j.getId() == JOB_ID && j.getIntervalMillis() == ms) return;
        }
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, CleanJob.class))
                .setPeriodic(ms)
                .setPersisted(true)
                .build());
    }
}
