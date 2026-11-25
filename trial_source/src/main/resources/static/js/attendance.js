
document.getElementById('showConfirmDialog').addEventListener('click', function() {
	
	// 確認ダイアログを表示
	const isConfirmed = confirm('更新します。よろしいですか？');
	
	if(!isConfirmed){
		return;
	}
})

