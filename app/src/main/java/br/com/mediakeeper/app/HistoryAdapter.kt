package br.com.mediakeeper.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.io.File

class HistoryAdapter(
    private val onOpen: (File) -> Unit,
    private val onShare: (File) -> Unit,
    private val onDelete: (File) -> Unit,
    private val onCancel: (String) -> Unit,
    private val onDismiss: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private var items: List<DownloadJob> = emptyList()

    fun submit(list: List<DownloadJob>) {
        val old = items
        items = list
        if (old.size != list.size || old.indices.any { old[it].id != list[it].id }) {
            notifyDataSetChanged()
            return
        }
        for (index in list.indices) {
            if (old[index] != list[index]) notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {

        private val title: TextView = view.findViewById(R.id.tvItemTitle)
        private val meta: TextView = view.findViewById(R.id.tvItemMeta)
        private val btnOpen: MaterialButton = view.findViewById(R.id.btnItemOpen)
        private val btnShare: MaterialButton = view.findViewById(R.id.btnItemShare)
        private val btnDelete: MaterialButton = view.findViewById(R.id.btnItemDelete)

        fun bind(job: DownloadJob) {
            val file = job.file
            val finished = job.status == JobStatus.DONE && file != null
            val working = job.status == JobStatus.RUNNING || job.status == JobStatus.QUEUED

            title.text = job.title
            meta.text = when (job.status) {
                JobStatus.QUEUED -> "Na fila - aguardando a vez"
                JobStatus.RUNNING -> "${job.progress}%  ${job.detail}"
                JobStatus.DONE -> job.detail
                JobStatus.ERROR -> "Erro: ${job.detail}"
                JobStatus.CANCELED -> "Cancelado"
            }

            btnOpen.visibility = if (finished) View.VISIBLE else View.GONE
            btnShare.visibility = if (finished) View.VISIBLE else View.GONE
            btnDelete.setText(
                when {
                    finished -> R.string.delete
                    working -> R.string.cancel
                    else -> R.string.remove
                }
            )

            btnOpen.setOnClickListener { file?.let(onOpen) }
            btnShare.setOnClickListener { file?.let(onShare) }
            btnDelete.setOnClickListener {
                when {
                    finished -> file?.let(onDelete)
                    working -> onCancel(job.id)
                    else -> onDismiss(job.id)
                }
            }
        }
    }
}
