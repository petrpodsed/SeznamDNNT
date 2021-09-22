import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { AppService } from 'src/app/app.service';
import { HistoryItem } from 'src/app/shared/history-item';
import { SolrDocument } from 'src/app/shared/solr-document';

@Component({
  selector: 'app-history-dialog',
  templateUrl: './history-dialog.component.html',
  styleUrls: ['./history-dialog.component.scss']
})
export class HistoryDialogComponent implements OnInit {

  displayedColumns = ['date','user', 'stav', 'license', 'comment']; //,'from','to','poznamka']
  history: HistoryItem[] = [];
  stavy: HistoryItem[] = [];

  constructor(
    public dialogRef: MatDialogRef<HistoryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: SolrDocument,
    private service: AppService) { }

  ngOnInit(): void {
    this.stavy = this.data.historie_stavu;
    this.stavy.map(h => {
      console.log(h.date);
      const d: string = h.date;
      const y = parseInt(d.substr(0,4)),
        m = parseInt(d.substr(4,2)) - 1,
        day = parseInt(d.substr(6,2));
      h.date = new Date(y,m,day);
    });
    console.log(this.stavy);
    // this.service.getHistory(this.data.identifier).subscribe(res => {
    //   this.history = res.response.docs;
    //   this.stavy = this.history.filter(item => {
    //     return item.changes.backward_patch.findIndex(p => p.path.indexOf('historie_stavu') > 0) > -1;
    //   });
    // });
  }

}
